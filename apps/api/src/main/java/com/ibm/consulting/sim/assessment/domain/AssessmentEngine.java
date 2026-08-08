package com.ibm.consulting.sim.assessment.domain;

import java.util.List;

/**
 * Deterministic competency scoring engine (§5.1, §5.2 — AI must not own final scoring
 * authority). Pure domain logic operating only on primitives already persisted by
 * other modules, so results are reproducible and independent of any AI call.
 */
public final class AssessmentEngine {

    private static final int EVIDENCE_ITEMS_FOR_FULL_CREDIT = 5;

    private AssessmentEngine() {}

    public static List<CompetencyScore> score(int researchEvidenceCount, int averageOutreachScore,
                                               int personaTrust, int personaInterest, int personaPatience,
                                               int proposalAlignmentScore) {
        int discoveryScore = Math.min(100,
                (int) Math.round(100.0 * researchEvidenceCount / EVIDENCE_ITEMS_FOR_FULL_CREDIT));
        int relationshipScore = (personaTrust + personaInterest + personaPatience) / 3;

        return List.of(
                new CompetencyScore("Research & Discovery", discoveryScore,
                        "%d research evidence items recorded".formatted(researchEvidenceCount)),
                new CompetencyScore("Outreach Effectiveness", averageOutreachScore,
                        "Average outreach evaluation score across all attempts"),
                new CompetencyScore("Relationship Building", relationshipScore,
                        "Final trust %d, interest %d, patience %d".formatted(personaTrust, personaInterest, personaPatience)),
                new CompetencyScore("Solution Alignment", proposalAlignmentScore,
                        "Proposal alignment score against discovered evidence and relationship state"));
    }

    public static int overall(List<CompetencyScore> scores) {
        return overall(scores, java.util.Map.of());
    }

    /**
     * Weighted overall score. {@code weights} maps competency name → weight percent;
     * any competency missing from the map (or an empty map altogether) falls back to
     * an equal share of the remaining weight, preserving the original equal-weight
     * average behaviour when no scenario-level rubric customisation exists.
     */
    public static int overall(List<CompetencyScore> scores, java.util.Map<String, Integer> weights) {
        if (scores.isEmpty()) {
            return 0;
        }
        if (weights == null || weights.isEmpty()) {
            return (int) Math.round(scores.stream().mapToInt(CompetencyScore::getScore).average().orElse(0));
        }
        double weightedSum = 0;
        double totalWeight = 0;
        for (CompetencyScore score : scores) {
            int weight = weights.getOrDefault(score.getCompetencyName(), 0);
            weightedSum += score.getScore() * weight;
            totalWeight += weight;
        }
        if (totalWeight == 0) {
            return (int) Math.round(scores.stream().mapToInt(CompetencyScore::getScore).average().orElse(0));
        }
        return (int) Math.round(weightedSum / totalWeight);
    }
}
