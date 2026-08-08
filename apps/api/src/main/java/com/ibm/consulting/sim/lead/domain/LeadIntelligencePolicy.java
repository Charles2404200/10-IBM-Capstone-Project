package com.ibm.consulting.sim.lead.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Determines what hidden {@link Lead} intelligence the learner has actually
 * <em>earned</em> through research — and, critically, derives the revealed
 * value from the learner's own evidence content rather than a static
 * scenario-preset string. "Evidence unlocks intelligence" means the panel
 * shows what the evidence says, not a canned fact that happens to exist
 * behind a reveal flag:
 *
 * <pre>
 * Stakeholder evidence found  → Decision Maker = that evidence's finding
 * Financial evidence found    → Budget Signal = that evidence's finding
 * Technology evidence found   → Technology Stack = that evidence's finding
 * Company news / market trend → Pain Severity = that evidence's finding
 * Decision Maker + Budget both → Potential Value unlocked (the scenario's
 *   designed opportunity size — the one figure that genuinely cannot be
 *   inferred from free-text evidence — cited against the evidence that
 *   earned it, for traceability)
 * </pre>
 *
 * Pure, stateless, side-effect-free (Policy pattern, matching
 * {@code EngagementPolicy}/{@code ReadinessPolicy}/{@code AssessmentEngine}
 * elsewhere in the codebase).
 */
public final class LeadIntelligencePolicy {

    private LeadIntelligencePolicy() {}

    /** A revealed (or not-yet-revealed) intelligence field: the learner-facing
     *  value plus which evidence items (by sequence number, e.g. "E-01") earned it —
     *  the traceability the Client Intelligence panel needs to explain itself. */
    public record Insight(String value, List<Integer> supportingEvidenceSequence) {
        static final Insight UNREVEALED = new Insight(null, List.of());
    }

    private static final Set<EvidenceType> COMPANY_CONTEXT_TYPES =
            EnumSet.of(EvidenceType.COMPANY_NEWS, EvidenceType.MARKET_TREND);

    /** The four research "coverage areas" a thorough investigation should span —
     *  used by {@link #confidenceScore} so breadth (not raw volume) drives confidence. */
    private static final List<Set<EvidenceType>> COVERAGE_BUCKETS = List.of(
            EnumSet.of(EvidenceType.STAKEHOLDER_PROFILE),
            EnumSet.of(EvidenceType.FINANCIAL_SIGNAL),
            EnumSet.of(EvidenceType.TECHNOLOGY_INDICATOR),
            COMPANY_CONTEXT_TYPES);

    public static Insight decisionMaker(List<ResearchEvidence> evidence) {
        return insightFor(evidence, EnumSet.of(EvidenceType.STAKEHOLDER_PROFILE));
    }

    public static Insight technologyStack(List<ResearchEvidence> evidence) {
        return insightFor(evidence, EnumSet.of(EvidenceType.TECHNOLOGY_INDICATOR));
    }

    public static Insight budgetSignal(List<ResearchEvidence> evidence) {
        return insightFor(evidence, EnumSet.of(EvidenceType.FINANCIAL_SIGNAL));
    }

    public static Insight painSeverity(List<ResearchEvidence> evidence) {
        return insightFor(evidence, COMPANY_CONTEXT_TYPES);
    }

    /** Sizing the opportunity is a synthesis step: it requires knowing both who
     *  decides (stakeholder evidence) and what they can spend (financial evidence).
     *  The value itself is the scenario's designed truth (not inferable from free
     *  text), but it is cited against exactly the evidence that earned its reveal. */
    public static Insight potentialValue(List<ResearchEvidence> evidence, String scenarioPotentialValueRange) {
        Insight decisionMaker = decisionMaker(evidence);
        Insight budgetSignal = budgetSignal(evidence);
        if (decisionMaker.value() == null || budgetSignal.value() == null || scenarioPotentialValueRange == null) {
            return Insight.UNREVEALED;
        }
        List<Integer> supporting = new ArrayList<>(decisionMaker.supportingEvidenceSequence());
        supporting.addAll(budgetSignal.supportingEvidenceSequence());
        return new Insight(scenarioPotentialValueRange, List.copyOf(supporting));
    }

    /** How many of the four research coverage areas have at least one finding. */
    public static int coverageCount(List<ResearchEvidence> evidence) {
        return (int) COVERAGE_BUCKETS.stream()
                .filter(bucket -> evidence.stream().anyMatch(e -> bucket.contains(e.getEvidenceType())))
                .count();
    }

    /** Average self-assessed reliability (LOW=33/MEDIUM=66/HIGH=100) across
     *  substantive (non-hypothesis) evidence — 0 if none collected yet. */
    public static int averageReliability(List<ResearchEvidence> evidence) {
        List<ResearchEvidence> substantive = substantiveEvidence(evidence);
        if (substantive.isEmpty()) return 0;
        return (int) Math.round(substantive.stream()
                .mapToInt(e -> reliabilityWeight(e.getConfidence()))
                .average()
                .orElse(0));
    }

    /** Combined 0-100 research confidence: breadth of coverage (70%) plus
     *  average evidence reliability (30%) — deliberately NOT evidence count,
     *  so spamming ten low-value notes in one category can't fake a HIGH score. */
    public static int confidenceScore(List<ResearchEvidence> evidence) {
        int coverageRatio = Math.round(coverageCount(evidence) * 100f / COVERAGE_BUCKETS.size());
        int reliability = averageReliability(evidence);
        return Math.round(coverageRatio * 0.7f + reliability * 0.3f);
    }

    /** Coarse-grained label the frontend renders as "Research confidence: LOW/MEDIUM/HIGH". */
    public static String confidenceLabel(List<ResearchEvidence> evidence) {
        int score = confidenceScore(evidence);
        if (score >= 75) return "HIGH";
        if (score >= 45) return "MEDIUM";
        return "LOW";
    }

    /** Human-readable factors behind the confidence score, for the "Why HIGH?" tooltip
     *  — e.g. "4/4 research areas covered", "3 high-reliability findings". */
    public static List<String> confidenceFactors(List<ResearchEvidence> evidence) {
        List<ResearchEvidence> substantive = substantiveEvidence(evidence);
        List<String> factors = new ArrayList<>();
        factors.add("%d/%d research areas covered".formatted(coverageCount(evidence), COVERAGE_BUCKETS.size()));

        long high = substantive.stream().filter(e -> e.getConfidence() == ConfidenceLevel.HIGH).count();
        long medium = substantive.stream().filter(e -> e.getConfidence() == ConfidenceLevel.MEDIUM).count();
        long low = substantive.stream().filter(e -> e.getConfidence() == ConfidenceLevel.LOW).count();
        if (high > 0) factors.add(high + " high-reliability finding" + (high > 1 ? "s" : ""));
        if (medium > 0) factors.add(medium + " medium-reliability finding" + (medium > 1 ? "s" : ""));
        if (low > 0) factors.add(low + " low-reliability finding" + (low > 1 ? "s" : ""));
        return List.copyOf(factors);
    }

    private static List<ResearchEvidence> substantiveEvidence(List<ResearchEvidence> evidence) {
        return evidence.stream().filter(e -> e.getEvidenceType() != EvidenceType.HYPOTHESIS).toList();
    }

    private static int reliabilityWeight(ConfidenceLevel confidence) {
        return switch (confidence) {
            case LOW -> 33;
            case MEDIUM -> 66;
            case HIGH -> 100;
        };
    }

    /** The most recent finding of the given type(s) is the learner's "current
     *  understanding" for that field; every matching item (not just the latest)
     *  is cited as supporting evidence for full traceability. */
    private static Insight insightFor(List<ResearchEvidence> evidence, Set<EvidenceType> types) {
        List<ResearchEvidence> matches = evidence.stream()
                .filter(e -> types.contains(e.getEvidenceType()))
                .sorted(Comparator.comparing(ResearchEvidence::getSequenceNo))
                .toList();
        if (matches.isEmpty()) return Insight.UNREVEALED;
        String value = matches.get(matches.size() - 1).getNote();
        List<Integer> sequence = matches.stream().map(ResearchEvidence::getSequenceNo).toList();
        return new Insight(value, sequence);
    }
}
