package com.ibm.consulting.sim.lead.domain;

import java.util.List;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

/**
 * Deterministic gate that decides whether a learner's Client Intelligence
 * research is thorough enough to advance the engagement from
 * {@code LEAD_SELECTED} to {@code RESEARCH_COMPLETED} (and therefore unlock
 * Outreach). Mirrors {@code ReadinessPolicy} (meeting preparation) — pure,
 * stateless domain logic with no Spring/JPA dependency, so the same business
 * rule can be evaluated both for the enforcing "complete research" command
 * and for a read-only "what's still missing" checklist the frontend renders.
 *
 * <p>Unlocking requires ALL of:
 * <ul>
 *   <li>at least {@link #MIN_EVIDENCE_COUNT} evidence items</li>
 *   <li>at least one {@link EvidenceType#STAKEHOLDER_PROFILE} item (you must
 *       have identified who you're dealing with)</li>
 *   <li>at least one {@link EvidenceType#HYPOTHESIS} item (you must have
 *       synthesised a testable theory, not just collected raw notes)</li>
 *   <li>a derived research confidence of at least {@link #MIN_CONFIDENCE_PERCENT}</li>
 * </ul>
 */
public final class ResearchReadinessPolicy {

    public static final int MIN_EVIDENCE_COUNT = 3;
    public static final int MIN_CONFIDENCE_PERCENT = 40;
    private static final int CONFIDENCE_PERCENT_PER_EVIDENCE = 20;

    private ResearchReadinessPolicy() {}

    /** Non-hypothesis evidence count — hypotheses are syntheses, not raw findings. */
    public static long evidenceCount(List<ResearchEvidence> evidence) {
        return evidence.stream().filter(e -> e.getEvidenceType() != EvidenceType.HYPOTHESIS).count();
    }

    public static boolean hasStakeholderEvidence(List<ResearchEvidence> evidence) {
        return evidence.stream().anyMatch(e -> e.getEvidenceType() == EvidenceType.STAKEHOLDER_PROFILE);
    }

    public static boolean hasHypothesis(List<ResearchEvidence> evidence) {
        return evidence.stream().anyMatch(e -> e.getEvidenceType() == EvidenceType.HYPOTHESIS);
    }

    /** Same evidence-count-driven scale as {@link LeadIntelligencePolicy}, expressed as a 0-100 percentage. */
    public static int confidencePercent(List<ResearchEvidence> evidence) {
        long count = evidenceCount(evidence);
        return (int) Math.min(100, count * CONFIDENCE_PERCENT_PER_EVIDENCE);
    }

    public static boolean isResearchComplete(List<ResearchEvidence> evidence) {
        return isResearchComplete(evidence, null);
    }

    public static boolean isResearchComplete(List<ResearchEvidence> evidence, DifficultyProfile profile) {
        int requiredEvidence = profile == null ? MIN_EVIDENCE_COUNT : profile.requiredEvidenceCount();
        int requiredConfidence = profile == null ? MIN_CONFIDENCE_PERCENT : profile.requiredConfidencePercent();
        return evidenceCount(evidence) >= requiredEvidence
                && hasStakeholderEvidence(evidence)
                && hasHypothesis(evidence)
                && confidencePercent(evidence) >= requiredConfidence;
    }
}
