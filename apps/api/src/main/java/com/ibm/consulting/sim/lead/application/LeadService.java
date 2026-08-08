package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.*;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
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

    public LeadService(LeadRepository leadRepository, ResearchEvidenceRepository evidenceRepository,
                       EngagementRepository engagementRepository) {
        this.leadRepository = leadRepository;
        this.evidenceRepository = evidenceRepository;
        this.engagementRepository = engagementRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "leadsByScenario", key = "#scenarioId")
    public List<LeadSummary> listForScenario(UUID scenarioId) {
        return leadRepository.findByScenarioId(scenarioId).stream()
                .map(LeadSummary::from)
                .toList();
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
                .sequenceNo(nextSequence)
                .supportingEvidenceIds(validatedSupportingIds)
                .build();
        evidenceRepository.save(evidence);
        return ResearchEvidenceSummary.from(evidence);
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
        return ResearchGateStatus.from(engagement.getState(), evidence);
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
            return ResearchGateStatus.from(engagement.getState(), evidence);
        }

        if (!ResearchReadinessPolicy.isResearchComplete(evidence)) {
            throw new ResearchNotReadyException(
                    ResearchReadinessPolicy.evidenceCount(evidence),
                    ResearchReadinessPolicy.hasStakeholderEvidence(evidence),
                    ResearchReadinessPolicy.hasHypothesis(evidence),
                    ResearchReadinessPolicy.confidencePercent(evidence));
        }

        engagement.transitionTo(EngagementState.HYPOTHESIS_READY,
                "Research completed with %d evidence items".formatted(evidence.size()));
        engagementRepository.save(engagement);
        return ResearchGateStatus.from(engagement.getState(), evidence);
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
        return LeadIntelligenceSummary.from(lead, evidence);
    }
}
