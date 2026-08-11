package com.ibm.consulting.sim.lead.domain;

import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic research-quality gate. It rewards a well-rounded, traceable
 * investigation instead of raw note volume. AI can explain this assessment,
 * but it never decides whether a learner unlocks the next phase.
 */
public final class ResearchReadinessPolicy {

    public static final int MIN_EVIDENCE_COUNT = 3;
    public static final int MIN_CONFIDENCE_PERCENT = 40;

    private static final List<Set<EvidenceType>> COVERAGE_BUCKETS = List.of(
            EnumSet.of(EvidenceType.STAKEHOLDER_PROFILE),
            EnumSet.of(EvidenceType.FINANCIAL_SIGNAL),
            EnumSet.of(EvidenceType.TECHNOLOGY_INDICATOR),
            EnumSet.of(EvidenceType.COMPANY_NEWS, EvidenceType.MARKET_TREND));

    /** Fully explainable quality decomposition surfaced in the learner UI. */
    public record QualityAssessment(
            int confidencePercent,
            int coverageCount,
            int reliabilityScore,
            int verificationScore,
            int relevanceScore,
            int corroborationScore,
            boolean groundedHypothesis,
            List<String> coaching) {}

    private ResearchReadinessPolicy() {}

    public static long evidenceCount(List<ResearchEvidence> evidence) {
        return substantiveEvidence(evidence).size();
    }

    public static int coverageCount(List<ResearchEvidence> evidence) {
        return (int) COVERAGE_BUCKETS.stream()
                .filter(bucket -> substantiveEvidence(evidence).stream()
                        .anyMatch(item -> bucket.contains(item.getEvidenceType())))
                .count();
    }

    public static int requiredCoverageCount(DifficultyProfile profile) {
        if (profile == null) return 2;
        return profile.level() == DifficultyLevel.HARD ? 3 : 2;
    }

    public static boolean hasStakeholderEvidence(List<ResearchEvidence> evidence) {
        return substantiveEvidence(evidence).stream()
                .anyMatch(e -> e.getEvidenceType() == EvidenceType.STAKEHOLDER_PROFILE
                        && isTrustedForDecision(e));
    }

    public static boolean hasHypothesis(List<ResearchEvidence> evidence) {
        return evidence.stream().anyMatch(e -> e.getEvidenceType() == EvidenceType.HYPOTHESIS);
    }

    public static boolean hasGroundedHypothesis(List<ResearchEvidence> evidence) {
        int substantiveCount = substantiveEvidence(evidence).size();
        return evidence.stream()
                .filter(e -> e.getEvidenceType() == EvidenceType.HYPOTHESIS)
                .anyMatch(hypothesis -> hypothesis.getNote() != null && hypothesis.getNote().trim().length() >= 40
                        // Legacy hypotheses did not persist links. Preserve them when their
                        // surrounding evidence is broad enough, while enforcing links when present.
                        && (hypothesis.getSupportingEvidenceIds().isEmpty()
                        ? substantiveCount >= MIN_EVIDENCE_COUNT && coverageCount(evidence) >= 2
                        : hypothesis.getSupportingEvidenceIds().size() >= 2));
    }

    public static QualityAssessment assess(List<ResearchEvidence> evidence) {
        List<ResearchEvidence> substantive = substantiveEvidence(evidence);
        if (substantive.isEmpty()) {
            return new QualityAssessment(0, 0, 0, 0, 0, 0, false,
                    List.of("Start with a client-specific source; aim to cover the company, stakeholder and operating context."));
        }

        int coverage = coverageCount(evidence);
        int reliability = average(substantive, item -> switch (item.getConfidence()) {
            case LOW -> 30;
            case MEDIUM -> 65;
            case HIGH -> 100;
        });
        int verification = average(substantive, item -> switch (item.getVerificationStatus()) {
            case VERIFIED -> 100;
            case CORROBORATED -> 80;
            case UNVERIFIED -> 35;
            case CONTRADICTED -> 0;
        });
        int relevance = average(substantive, ResearchEvidence::getRelevanceScore);
        int corroboration = corroborationScore(substantive, coverage);
        int coverageScore = Math.round(coverage * 100f / COVERAGE_BUCKETS.size());
        int total = Math.round(coverageScore * .35f + reliability * .20f + verification * .20f
                + relevance * .15f + corroboration * .10f);

        List<String> coaching = new ArrayList<>();
        if (coverage < 2) coaching.add("Broaden the investigation across at least two client areas; more notes in one area will not materially raise confidence.");
        if (verification < 60) coaching.add("Validate or corroborate unverified inputs before relying on them in your hypothesis.");
        if (relevance < 60) coaching.add("Prioritise sources that directly explain this client's decision, operating pain or constraints.");
        if (!hasGroundedHypothesis(evidence)) coaching.add("Link at least two findings to a clear, testable hypothesis about the underlying client problem.");
        if (coaching.isEmpty()) coaching.add("Your evidence is broad, relevant and traceable. Use the hypothesis to name the remaining assumption to validate in outreach.");

        return new QualityAssessment(total, coverage, reliability, verification, relevance, corroboration,
                hasGroundedHypothesis(evidence), List.copyOf(coaching));
    }

    public static int confidencePercent(List<ResearchEvidence> evidence) {
        return assess(evidence).confidencePercent();
    }

    public static boolean isResearchComplete(List<ResearchEvidence> evidence) {
        return isResearchComplete(evidence, null);
    }

    public static boolean isResearchComplete(List<ResearchEvidence> evidence, DifficultyProfile profile) {
        int requiredEvidence = profile == null ? MIN_EVIDENCE_COUNT : profile.requiredEvidenceCount();
        int requiredConfidence = profile == null ? MIN_CONFIDENCE_PERCENT : profile.requiredConfidencePercent();
        QualityAssessment quality = assess(evidence);
        return evidenceCount(evidence) >= requiredEvidence
                && coverageCount(evidence) >= requiredCoverageCount(profile)
                && hasStakeholderEvidence(evidence)
                && quality.groundedHypothesis()
                && quality.confidencePercent() >= requiredConfidence;
    }

    private static List<ResearchEvidence> substantiveEvidence(List<ResearchEvidence> evidence) {
        return evidence.stream().filter(e -> e.getEvidenceType() != EvidenceType.HYPOTHESIS).toList();
    }

    private static int corroborationScore(List<ResearchEvidence> evidence, int coverage) {
        if (evidence.size() < 2) return 15;
        boolean linked = evidence.stream().anyMatch(item -> !item.getSupportingEvidenceIds().isEmpty());
        if (coverage >= 3 || linked) return 100;
        return coverage == 2 ? 70 : 30;
    }

    private static int average(List<ResearchEvidence> evidence, java.util.function.ToIntFunction<ResearchEvidence> mapper) {
        return (int) Math.round(evidence.stream().mapToInt(mapper).average().orElse(0));
    }

    private static boolean isTrustedForDecision(ResearchEvidence evidence) {
        return evidence.getVerificationStatus() != EvidenceVerificationStatus.CONTRADICTED
                && (evidence.getOrigin() != EvidenceOrigin.USER_SUPPLIED
                || evidence.getVerificationStatus() == EvidenceVerificationStatus.CORROBORATED
                || evidence.getVerificationStatus() == EvidenceVerificationStatus.VERIFIED);
    }
}
