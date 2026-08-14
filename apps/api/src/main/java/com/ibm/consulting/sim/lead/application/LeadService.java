package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.*;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringConfigService;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final EngagementRepository engagementRepository;
    private final DifficultyProfileService difficultyProfileService;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioAuthoringConfigService authoringConfigService;

    public LeadService(LeadRepository leadRepository, ResearchEvidenceRepository evidenceRepository,
                       EngagementRepository engagementRepository, DifficultyProfileService difficultyProfileService,
                       ScenarioRepository scenarioRepository, ScenarioAuthoringConfigService authoringConfigService) {
        this.leadRepository = leadRepository;
        this.evidenceRepository = evidenceRepository;
        this.engagementRepository = engagementRepository;
        this.difficultyProfileService = difficultyProfileService;
        this.scenarioRepository = scenarioRepository;
        this.authoringConfigService = authoringConfigService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "leadsByScenario", key = "#scenarioId")
    public List<LeadSummary> listForScenario(UUID scenarioId) {
        return leadRepository.findByScenarioId(scenarioId).stream()
                .map(LeadSummary::from)
                .toList();
    }

    /** Indexed and cached catalogue query used by Command Centre. It never loads the full catalogue into memory. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.LEAD_CATALOG_CACHE, key = "#query.cacheKey()")
    public LeadCatalogResponse listCatalog(LeadCatalogQuery query) {
        return LeadCatalogResponse.from(leadRepository.findCatalog(query));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.LEAD_CATALOG_FACETS_CACHE, key = "'industries'")
    public List<String> catalogIndustries() {
        return leadRepository.findCatalogIndustries();
    }

    @Transactional
    public void selectLead(UUID engagementId, UUID leadId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));

        // Idempotent no-op: re-selecting the same lead (e.g. a stale UI retry)
        // succeeds silently instead of hitting the state-machine guard below.
        if (leadId.equals(engagement.getSelectedLeadId())) {
            return;
        }
        // A lead is only selectable while the engagement is still in DRAFT —
        // once selected, it is locked in for the rest of the engagement's
        // lifecycle. Reject with a precise, actionable message rather than
        // letting the generic InvalidTransitionException surface.
        if (engagement.getState() != EngagementState.QUALIFYING) {
            throw new LeadAlreadySelectedException(engagementId);
        }

        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new NotFoundException("Lead", leadId));
        if (!lead.getScenarioId().equals(engagement.getScenarioId())) {
            throw new LeadNotInScenarioException(leadId, engagement.getScenarioId());
        }
        engagement.selectLead(leadId);
        engagementRepository.save(engagement);
    }

    @Transactional
    public ResearchEvidenceSummary saveEvidence(UUID engagementId, UUID userId,
                                                String note, String hypothesis,
                                                EvidenceType evidenceType,
                                                String sourceUrl, String sourceTitle,
                                                EvidenceOrigin origin, EvidenceVerificationStatus verificationStatus,
                                                LocalDate occurredOn, ConfidenceLevel confidence,
                                                Integer relevanceScore,
                                                Set<UUID> supportingEvidenceIds) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        UUID leadId = engagement.getSelectedLeadId();
        if (leadId == null) {
            throw new IllegalStateException("No lead selected for engagement");
        }

        Set<UUID> validatedSupportingIds = validateSupportingEvidence(engagementId, supportingEvidenceIds);
        int nextSequence = (int) evidenceRepository.countByEngagementId(engagementId) + 1;

        ResearchEvidence evidence = ResearchEvidence.builder()
                .engagementId(engagementId)
                .leadId(leadId)
                .note(note)
                .hypothesis(hypothesis)
                .evidenceType(evidenceType)
                .sourceUrl(sourceUrl)
                .sourceTitle(sourceTitle)
                .origin(origin)
                .verificationStatus(verificationStatus)
                .occurredOn(occurredOn)
                .confidence(confidence)
                .relevanceScore(normalizeRelevance(origin, relevanceScore))
                .sequenceNo(nextSequence)
                .supportingEvidenceIds(validatedSupportingIds)
                .build();
        evidenceRepository.save(evidence);
        return ResearchEvidenceSummary.from(evidence);
    }

    /** The browser can suggest relevance from a reviewed artifact, but unverified
     * learner input can never claim the same weight as scenario-controlled evidence. */
    private int normalizeRelevance(EvidenceOrigin origin, Integer requestedScore) {
        int defaultScore = switch (origin == null ? EvidenceOrigin.USER_SUPPLIED : origin) {
            case SCENARIO_CURATED -> 85;
            case AI_SYNTHESIZED -> 80;
            case MEETING_DISCOVERY -> 90;
            case USER_SUPPLIED -> 35;
        };
        int score = requestedScore == null ? defaultScore : Math.max(0, Math.min(100, requestedScore));
        return origin == EvidenceOrigin.USER_SUPPLIED ? Math.min(score, 45) : score;
    }

    /** Guards against a hypothesis citing evidence IDs from another engagement or that don't exist. */
    private Set<UUID> validateSupportingEvidence(UUID engagementId, Set<UUID> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return Set.of();
        }
        List<ResearchEvidence> found = evidenceRepository.findByIdInAndEngagementId(
                requestedIds.stream().toList(), engagementId);
        if (found.size() != requestedIds.size()) {
            throw new IllegalArgumentException("One or more supporting evidence IDs are invalid for this engagement");
        }
        return requestedIds;
    }

    @Transactional(readOnly = true)
    public List<ResearchEvidenceSummary> listEvidence(UUID engagementId, UUID userId) {
        engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        return evidenceRepository.findByEngagementId(engagementId).stream()
                .map(ResearchEvidenceSummary::from)
                .toList();
    }

    /**
     * Read-only "requirements checklist" the Client Intelligence page polls to
     * decide whether to enable "Proceed to Outreach" and which conditions are
     * still unmet (see {@link ResearchReadinessPolicy}).
     */
    @Transactional(readOnly = true)
    public ResearchGateStatus getResearchGateStatus(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        List<ResearchEvidence> evidence = evidenceRepository.findByEngagementId(engagementId);
        return ResearchGateStatus.from(engagement.getState(), evidence, difficultyProfileService.forEngagement(engagement));
    }

    /**
     * Advances the engagement from {@code LEAD_SELECTED} to
     * {@code RESEARCH_COMPLETED}, unlocking Outreach — the missing transition
     * that previously left engagements permanently stuck in
     * {@code LEAD_SELECTED} even after the learner had gathered evidence.
     * Idempotent: calling this again once research is already completed is a
     * no-op rather than an invalid-transition error.
     */
    @Transactional
    public ResearchGateStatus completeResearch(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        List<ResearchEvidence> evidence = evidenceRepository.findByEngagementId(engagementId);

        if (engagement.getState() != EngagementState.CLIENT_INTELLIGENCE) {
            // Already past this gate (or not there yet) — return current status without re-transitioning.
            return ResearchGateStatus.from(engagement.getState(), evidence, difficultyProfileService.forEngagement(engagement));
        }

        var profile = difficultyProfileService.forEngagement(engagement);
        if (!ResearchReadinessPolicy.isResearchComplete(evidence, profile)) {
            throw new ResearchNotReadyException(evidence, profile);
        }

        engagement.transitionTo(EngagementState.HYPOTHESIS_READY,
                "Research completed with %d evidence items".formatted(evidence.size()));
        engagementRepository.save(engagement);
        return ResearchGateStatus.from(engagement.getState(), evidence, profile);
    }

    /**
     * The "Client Profile" workspace panel: reveals the selected lead's hidden
     * intelligence fields progressively as research evidence accumulates
     * (see {@link com.ibm.consulting.sim.lead.domain.LeadIntelligencePolicy}).
     */
    @Transactional(readOnly = true)
    public LeadIntelligenceSummary getIntelligence(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        UUID leadId = engagement.getSelectedLeadId();
        if (leadId == null) {
            throw new IllegalStateException("No lead selected for engagement");
        }
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new NotFoundException("Lead", leadId));
        List<ResearchEvidence> evidence = evidenceRepository.findByEngagementId(engagementId);
        var authoringConfig = scenarioRepository.findById(engagement.getScenarioId())
                .map(authoringConfigService::forScenario)
                .orElseGet(com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig::defaults);
        return LeadIntelligenceSummary.from(lead, evidence, authoringConfig);
    }
}
