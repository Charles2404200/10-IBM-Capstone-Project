package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.ConversationActor;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingCompletionOutcome;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingStatus;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.proposal.domain.*;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Proposal application boundary. Canonical evidence and transcript entries are
 * exposed as read-only sources; learners own the proposal draft. LLM results are
 * advisory only and never participate in the deterministic client outcome.
 */
@Service
public class ProposalService {
    private static final int PROMPT_VERSION = 1;

    private final ProposalRepository proposalRepository;
    private final EngagementRepository engagementRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final PersonaStateRepository personaStateRepository;
    private final MeetingRepository meetingRepository;
    private final ConversationTurnRepository turnRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;
    private final PersonaCatalogService personaCatalogService;
    private final DifficultyProfileService difficultyProfileService;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;
    /** Avoids duplicate provider calls when two browser requests review the same draft concurrently. */
    private final ConcurrentMap<String, CompletableFuture<ProposalReviewResponse>> reviewRequests = new ConcurrentHashMap<>();

    public ProposalService(ProposalRepository proposalRepository,
                           EngagementRepository engagementRepository,
                           ResearchEvidenceRepository evidenceRepository,
                           PersonaStateRepository personaStateRepository,
                           MeetingRepository meetingRepository,
                           ConversationTurnRepository turnRepository,
                           AiOrchestrationService aiOrchestrationService,
                           ObjectMapper objectMapper,
                           PersonaCatalogService personaCatalogService,
                           DifficultyProfileService difficultyProfileService,
                           CacheManager cacheManager,
                           ApplicationEventPublisher eventPublisher) {
        this.proposalRepository = proposalRepository;
        this.engagementRepository = engagementRepository;
        this.evidenceRepository = evidenceRepository;
        this.personaStateRepository = personaStateRepository;
        this.meetingRepository = meetingRepository;
        this.turnRepository = turnRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
        this.personaCatalogService = personaCatalogService;
        this.difficultyProfileService = difficultyProfileService;
        this.cacheManager = cacheManager;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public ProposalWorkspaceResponse workspace(UUID engagementId, UUID userId) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        ProposalResponse proposal = proposalRepository.findByEngagementId(engagementId)
                .map(ProposalResponse::from)
                .orElse(null);
        return new ProposalWorkspaceResponse(proposal, sources(engagement));
    }

    @Transactional
    public ProposalResponse saveDraft(UUID engagementId, UUID userId, ProposalDraftContent content) {
        Engagement engagement = loadEditableEngagement(engagementId, userId);
        Proposal proposal = proposalRepository.findByEngagementId(engagementId).orElse(null);
        if (proposal == null) {
            proposal = Proposal.draft(engagementId, content);
        } else if (proposal.getStatus() == ProposalStatus.SUBMITTED) {
            throw new ProposalAlreadySubmittedException();
        } else {
            proposal.updateDraft(content);
        }
        proposalRepository.save(proposal);
        if (engagement.getState() == EngagementState.DISCOVERY_COMPLETE) {
            engagement.transitionTo(EngagementState.PROPOSAL_DRAFT, "Proposal draft created");
            engagementRepository.save(engagement);
        }
        return ProposalResponse.from(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalReviewResponse review(UUID engagementId, UUID userId, ProposalDraftContent content) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        List<ProposalSource> sources = sources(engagement);
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);
        String cacheKey = reviewCacheKey(engagementId, content, sources, profile);
        ProposalReviewResponse cachedReview = readCachedReview(cacheKey);
        if (cachedReview != null) {
            return cachedReview;
        }
        CompletableFuture<ProposalReviewResponse> pending = new CompletableFuture<>();
        CompletableFuture<ProposalReviewResponse> existing = reviewRequests.putIfAbsent(cacheKey, pending);
        if (existing != null) {
            return awaitInFlightReview(existing);
        }

        try {
            ProposalReviewResponse response = createReview(engagementId, content, sources, profile);
            writeCachedReview(cacheKey, response);
            pending.complete(response);
            return response;
        } catch (RuntimeException failure) {
            pending.completeExceptionally(failure);
            throw failure;
        } finally {
            reviewRequests.remove(cacheKey, pending);
        }
    }

    private ProposalReviewResponse createReview(UUID engagementId, ProposalDraftContent content,
                                                List<ProposalSource> sources, DifficultyProfile profile) {
        List<ProposalValidationIssue> issues = ProposalValidationEngine.validate(content, sources, profile);
        List<ClientAlignmentItem> alignment = ProposalValidationEngine.alignment(content, sources);
        ProposalReviewNarrative narrative = aiOrchestrationService.execute(
                "proposal_review", engagementId, reviewPrompt(content, alignment, issues), PROMPT_VERSION,
                new ProposalReviewParser(objectMapper), () -> ProposalReviewNarrative.fallback(issues));
        return reviewResponse(content, sources, issues, alignment, narrative);
    }

    @Transactional(readOnly = true)
    public ProposalChallengeResponse challenge(UUID engagementId, UUID userId, ProposalDraftContent content) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        List<ProposalSource> sources = sources(engagement);
        return aiOrchestrationService.execute(
                "proposal_challenge", engagementId, challengePrompt(content, sources), PROMPT_VERSION,
                new ProposalChallengeParser(objectMapper), () -> deterministicConcerns(content, sources));
    }

    @Transactional
    public ProposalResponse submit(UUID engagementId, UUID userId, ProposalDraftContent content,
                                   boolean enforceWorkspaceGate) {
        Engagement engagement = loadEditableEngagement(engagementId, userId);
        Proposal proposal = proposalRepository.findByEngagementId(engagementId).orElse(null);
        if (proposal != null && proposal.getStatus() == ProposalStatus.SUBMITTED) {
            throw new ProposalAlreadySubmittedException();
        }

        List<ProposalSource> sources = sources(engagement);
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);
        List<ProposalValidationIssue> issues = ProposalValidationEngine.validate(content, sources, profile);
        if (enforceWorkspaceGate && issues.stream().anyMatch(issue -> "BLOCKING".equals(issue.severity()))) {
            throw new ProposalValidationException(issues);
        }

        if (proposal == null) proposal = Proposal.draft(engagementId, content);
        else proposal.updateDraft(content);

        PersonaState state = personaStateRepository.findByEngagementId(engagementId)
                .orElseGet(() -> PersonaState.initial(engagementId));
        ProposalDecisionSnapshot decisionSnapshot = ProposalDecisionEngine.evaluate(content,
                sources.stream().map(source -> new ProposalDecisionSource(source.id(), source.content(), source.type())).toList(),
                state.getTrust(), state.getInterest(), state.getPatience(), profile);
        PersonaProfile persona = personaCatalogService.getPersona(engagement.getPersonaId());
        proposal.submit();
        proposal.resolve(decisionSnapshot, ProposalClientDecision.fromDecision(decisionSnapshot).message());
        proposalRepository.save(proposal);

        if (engagement.getState() == EngagementState.DISCOVERY_COMPLETE) {
            engagement.transitionTo(EngagementState.PROPOSAL_DRAFT, "Proposal draft opened during submission");
        }
        engagement.transitionTo(EngagementState.PROPOSAL_SUBMITTED, "Proposal submitted");
        engagement.transitionTo(EngagementState.CLIENT_DECISION,
                "Client decision: " + decisionSnapshot.outcome());
        engagementRepository.save(engagement);
        eventPublisher.publishEvent(new ProposalDecisionSubmittedEvent(engagementId, content, sources, persona,
                decisionSnapshot));
        return ProposalResponse.from(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalResponse get(UUID engagementId, UUID userId) {
        loadOwnedEngagement(engagementId, userId);
        Proposal proposal = proposalRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new NotFoundException("Proposal for engagement", engagementId));
        return ProposalResponse.from(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalDecisionExplanationResponse explainDecision(UUID engagementId, UUID userId) {
        Proposal proposal = submittedProposal(engagementId, userId);
        return aiOrchestrationService.execute("proposal_decision_explanation", engagementId,
                decisionExplanationPrompt(proposal), PROMPT_VERSION, new ProposalDecisionExplanationParser(objectMapper),
                () -> new ProposalDecisionExplanationResponse(deterministicDecisionExplanation(proposal)));
    }

    @Transactional(readOnly = true)
    public ProposalDecisionExplanationResponse counterfactual(UUID engagementId, UUID userId) {
        Proposal proposal = submittedProposal(engagementId, userId);
        return aiOrchestrationService.execute("proposal_counterfactual", engagementId,
                counterfactualPrompt(proposal), PROMPT_VERSION, new ProposalDecisionExplanationParser(objectMapper),
                () -> new ProposalDecisionExplanationResponse(deterministicCounterfactual(proposal)));
    }

    private ProposalReviewResponse reviewResponse(ProposalDraftContent content, List<ProposalSource> sources,
                                                  List<ProposalValidationIssue> issues,
                                                  List<ClientAlignmentItem> alignment,
                                                  ProposalReviewNarrative narrative) {
        return new ProposalReviewResponse(
                issues.stream().noneMatch(issue -> "BLOCKING".equals(issue.severity())),
                issues, alignment,
                ProposalValidationEngine.problemScore(content),
                ProposalValidationEngine.evidenceScore(content, sources),
                ProposalValidationEngine.alignmentScore(alignment),
                ProposalValidationEngine.commercialScore(content),
                ProposalValidationEngine.riskScore(content),
                ProposalValidationEngine.feasibilityScore(content),
                narrative.executiveFeedback(), narrative.improvementActions());
    }

    private Proposal submittedProposal(UUID engagementId, UUID userId) {
        loadOwnedEngagement(engagementId, userId);
        Proposal proposal = proposalRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new NotFoundException("Proposal for engagement", engagementId));
        if (proposal.getStatus() != ProposalStatus.SUBMITTED) throw new ProposalNotSubmittedException();
        return proposal;
    }

    private String decisionExplanationPrompt(Proposal proposal) {
        return """
                You are a consulting coach explaining a client decision. You may only explain the deterministic snapshot below.
                Do not change its outcome, scores, conditions, or infer new client facts. Return ONLY JSON: {"message": string}.
                Outcome: %s. Decision score: %d. Confidence: %s%%.
                Dimensions: %s
                Decision factors: %s
                """.formatted(legacyOutcome(proposal), proposal.getAlignmentScore(), valueOr(proposal.getDecisionConfidence(), proposal.getAlignmentScore()),
                proposal.getDecisionDimensions(), proposal.getDecisionInsights());
    }

    private String counterfactualPrompt(Proposal proposal) {
        return """
                You are a consulting coach. Based only on this deterministic decision snapshot, describe the highest-impact changes
                the learner could have made. Do not claim an exact new score or change the outcome. Return ONLY JSON: {"message": string}.
                Outcome: %s. Dimensions: %s. Factors: %s. Evidence impacts: %s
                """.formatted(legacyOutcome(proposal), proposal.getDecisionDimensions(), proposal.getDecisionInsights(), proposal.getEvidenceImpacts());
    }

    private String deterministicDecisionExplanation(Proposal proposal) {
        return "The outcome was determined by the weighted decision dimensions. " + proposal.getDecisionRationale()
                + " Review the strengths, concerns and approval conditions to see exactly what influenced the client decision.";
    }

    private String deterministicCounterfactual(Proposal proposal) {
        List<String> actions = proposal.getDecisionInsights().stream()
                .filter(insight -> "CONCERN".equals(insight.getCategory()))
                .map(ProposalDecisionInsight::getDetail).limit(3).toList();
        return actions.isEmpty()
                ? "The proposal was already well aligned. Strengthen the evidence behind each material claim before expanding scope."
                : "The highest-impact improvements would be: " + String.join(" ", actions);
    }

    private ClientDecisionOutcome legacyOutcome(Proposal proposal) {
        if (proposal.getClientDecisionOutcome() != null) return proposal.getClientDecisionOutcome();
        return proposal.getDecision() == ProposalDecision.WON ? ClientDecisionOutcome.PROPOSAL_ACCEPTED : ClientDecisionOutcome.REJECTED;
    }

    private int valueOr(Integer value, int fallback) { return value == null ? fallback : value; }

    /**
     * A review is reusable only when every input that can influence it is identical.
     * The source snapshot includes newly revealed meeting facts, while the difficulty
     * profile captures the rules frozen onto the engagement at its start.
     */
    private String reviewCacheKey(UUID engagementId, ProposalDraftContent content,
                                  List<ProposalSource> sources, DifficultyProfile profile) {
        String snapshot = "prompt=" + PROMPT_VERSION + "|draft=" + content
                + "|sources=" + sources + "|difficulty=" + profile;
        return engagementId + ":" + sha256(snapshot);
    }

    private ProposalReviewResponse readCachedReview(String key) {
        Cache cache = cacheManager.getCache(CacheConfig.PROPOSAL_REVIEW_CACHE);
        return cache == null ? null : cache.get(key, ProposalReviewResponse.class);
    }

    private void writeCachedReview(String key, ProposalReviewResponse review) {
        Cache cache = cacheManager.getCache(CacheConfig.PROPOSAL_REVIEW_CACHE);
        if (cache != null) {
            cache.put(key, review);
        }
    }

    private ProposalReviewResponse awaitInFlightReview(CompletableFuture<ProposalReviewResponse> review) {
        try {
            return review.join();
        } catch (CompletionException failure) {
            throw new IllegalStateException("Concurrent proposal review failed", failure.getCause());
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private List<ProposalSource> sources(Engagement engagement) {
        List<ProposalSource> sources = new ArrayList<>();
        evidenceRepository.findByEngagementId(engagement.getId()).forEach(evidence -> sources.add(new ProposalSource(
                "evidence:" + evidence.getId(), "E-" + String.format("%02d", evidence.getSequenceNo())
                        + " " + sourceLabel(evidence),
                "RESEARCH_EVIDENCE", evidence.getNote(), evidence.getConfidence().name())));
        meetingRepository.findByEngagementId(engagement.getId()).ifPresent(meeting ->
                turnRepository.findByMeetingIdOrderBySequenceAsc(meeting.getId()).stream()
                        .filter(turn -> turn.getActor() == ConversationActor.PERSONA)
                        .forEach(turn -> sources.add(new ProposalSource(
                                "meeting:" + turn.getId(), "M-" + turn.getSequence() + " Client discovery",
                                "MEETING_DISCOVERY", turn.getContent(), "HIGH"))));
        return List.copyOf(sources);
    }

    private String sourceLabel(ResearchEvidence evidence) {
        return evidence.getSourceTitle() == null || evidence.getSourceTitle().isBlank()
                ? evidence.getEvidenceType().name().replace('_', ' ')
                : evidence.getSourceTitle();
    }

    private ProposalChallengeResponse deterministicConcerns(ProposalDraftContent content, List<ProposalSource> sources) {
        List<ProposalValidationIssue> issues = ProposalValidationEngine.validate(content, sources);
        List<String> concerns = new ArrayList<>(issues.stream().limit(3)
                .map(issue -> "How will you address this: " + issue.message()).toList());
        if (content.businessOutcomes().isEmpty()) concerns.add("What measurable return should the client expect from the pilot?");
        if (content.risks().isEmpty()) concerns.add("How will you limit operational disruption and provide a rollback path?");
        if (concerns.isEmpty()) concerns.add("Which proposal commitment is most important for the executive sponsor to validate first?");
        return new ProposalChallengeResponse(List.copyOf(concerns));
    }

    private String reviewPrompt(ProposalDraftContent content, List<ClientAlignmentItem> alignment,
                                List<ProposalValidationIssue> issues) {
        return """
                You are an executive proposal coach. You review, never rewrite, the learner's proposal.
                The deterministic engine has already produced validation findings; do not contradict or score the proposal.
                Return ONLY JSON: {"executiveFeedback": string, "improvementActions": [string, string, string]}.
                Proposal problem: %s
                Solution: %s
                Validation: %s
                Client alignment: %s
                """.formatted(content.problemStatement(), content.solutionStrategy(), issues, alignment);
    }

    private String challengePrompt(ProposalDraftContent content, List<ProposalSource> sources) {
        String clientContext = sources.stream().limit(6).map(source -> source.label() + ": " + source.content())
                .reduce("", (left, right) -> left + "\n" + right);
        return """
                You are a client committee challenging a consulting proposal. Ask concise questions only; do not invent facts.
                Return ONLY JSON: {"concerns": [string, string, string]}.
                Proposal: %s
                Client evidence: %s
                """.formatted(content.problemStatement() + "\n" + content.solutionStrategy(), clientContext);
    }

    private Engagement loadEditableEngagement(UUID engagementId, UUID userId) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        recoverPassedMeetingState(engagement);
        if (engagement.getState() != EngagementState.DISCOVERY_COMPLETE
                && engagement.getState() != EngagementState.PROPOSAL_DRAFT) {
            throw new InvalidProposalStateException(engagement.getState());
        }
        return engagement;
    }

    /**
     * A proposal is only editable after discovery. Older deployments could leave
     * an engagement in IN_MEETING after persisting a passed meeting; repair that
     * narrow, verifiable state drift without weakening the normal lifecycle gate.
     */
    private void recoverPassedMeetingState(Engagement engagement) {
        if (engagement.getState() != EngagementState.IN_MEETING) {
            return;
        }
        boolean passedMeeting = meetingRepository.findByEngagementId(engagement.getId())
                .filter(meeting -> meeting.getStatus() == MeetingStatus.COMPLETED)
                .map(meeting -> meeting.getCompletionOutcome() == MeetingCompletionOutcome.PASSED)
                .orElse(false);
        if (passedMeeting) {
            engagement.transitionTo(EngagementState.DISCOVERY_COMPLETE,
                    "Recovered discovery completion from passed live meeting");
            engagementRepository.save(engagement);
        }
    }

    private Engagement loadOwnedEngagement(UUID engagementId, UUID userId) {
        return engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
    }

    public static class InvalidProposalStateException extends DomainException {
        public InvalidProposalStateException(EngagementState state) {
            super("Cannot edit a proposal in state: " + state);
        }
    }

    public static class ProposalValidationException extends DomainException {
        private final List<ProposalValidationIssue> issues;
        public ProposalValidationException(List<ProposalValidationIssue> issues) {
            super("Resolve proposal validation findings before submission");
            this.issues = List.copyOf(issues);
        }
        public List<ProposalValidationIssue> getIssues() { return issues; }
    }

    public static class ProposalNotSubmittedException extends DomainException {
        public ProposalNotSubmittedException() { super("A client decision is not available until the proposal is submitted"); }
    }
}
