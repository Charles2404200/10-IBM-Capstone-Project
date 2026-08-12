package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.proposal.domain.Proposal;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.proposal.domain.ProposalStatus;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Separates the non-authoritative AI response wording from the submit path.
 * The outcome is already durable and deterministic when this listener runs.
 */
@Component
class ProposalDecisionNarrativeEnricher {
    private static final int PROMPT_VERSION = 1;

    private final ProposalRepository proposalRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    ProposalDecisionNarrativeEnricher(ProposalRepository proposalRepository,
                                      AiOrchestrationService aiOrchestrationService,
                                      ObjectMapper objectMapper,
                                      CacheManager cacheManager) {
        this.proposalRepository = proposalRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    @Async("proposalNarrativeExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enrich(ProposalDecisionSubmittedEvent event) {
        String key = cacheKey(event);
        String message = cachedMessage(key);
        if (message == null) {
            message = aiOrchestrationService.execute("proposal_client_decision", event.engagementId(),
                    prompt(event), PROMPT_VERSION, new ProposalClientDecisionParser(objectMapper),
                    () -> ProposalClientDecision.fromDecision(event.decision())).message();
            cacheMessage(key, message);
        }
        String resolvedMessage = message;
        proposalRepository.findByEngagementId(event.engagementId())
                .filter(proposal -> proposal.getStatus() == ProposalStatus.SUBMITTED)
                .ifPresent(proposal -> {
                    proposal.updateClientResponse(resolvedMessage);
                    proposalRepository.save(proposal);
                });
    }

    private String prompt(ProposalDecisionSubmittedEvent event) {
        String context = event.sources().stream().limit(5)
                .map(source -> source.label() + ": " + source.content()).reduce("", (left, right) -> left + "\n" + right);
        return """
                You are %s, %s at %s. Your communication style is: %s.
                The backend has already decided the proposal outcome. Write a concise client response grounded only in the decision,
                proposal and evidence. Do not change, soften, or challenge the supplied outcome. Return ONLY JSON: {"message": string}.
                Decision: %s
                Rationale: %s
                Proposal: %s
                Client evidence: %s
                """.formatted(event.persona().getName(), event.persona().getJobTitle(), event.persona().getOrganisation(),
                event.persona().getCommunicationStyle(), event.decision().outcome(), event.decision().rationale(),
                event.content().problemStatement() + "\n" + event.content().solutionStrategy(), context);
    }

    private String cacheKey(ProposalDecisionSubmittedEvent event) {
        return event.engagementId() + ":" + sha256("v=" + PROMPT_VERSION + "|" + event.decision()
                + "|" + event.content() + "|" + event.sources() + "|" + event.persona());
    }

    private String cachedMessage(String key) {
        Cache cache = cacheManager.getCache(CacheConfig.PROPOSAL_DECISION_NARRATIVE_CACHE);
        return cache == null ? null : cache.get(key, String.class);
    }

    private void cacheMessage(String key, String message) {
        Cache cache = cacheManager.getCache(CacheConfig.PROPOSAL_DECISION_NARRATIVE_CACHE);
        if (cache != null) cache.put(key, message);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) hex.append(String.format("%02x", current));
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
