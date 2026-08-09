package com.ibm.consulting.sim.scenario.domain;

/**
 * Resolved, deterministic gameplay parameters. This record is persisted as a
 * scenario configuration and snapshotted to the engagement at start, keeping
 * published scenario edits from changing a learner's in-progress simulation.
 */
public record DifficultyProfile(
        DifficultyLevel level,
        int researchArtifactsPerAction,
        int distractorArtifactsPerAction,
        int contradictionCount,
        int initialTrust,
        int initialInterest,
        int initialPatience,
        int meetingTurnLimit,
        boolean budgetVisible,
        int timelinePressureDays,
        int requiredEvidenceCount,
        int requiredConfidencePercent,
        int outreachAcceptanceThreshold,
        int proposalEvidenceCoverageThreshold,
        int personaResistance,
        int scoringTolerance) {

    public DifficultyProfile {
        level = level == null ? DifficultyLevel.MEDIUM : level;
        researchArtifactsPerAction = between(researchArtifactsPerAction, 2, 8);
        distractorArtifactsPerAction = between(distractorArtifactsPerAction, 0, researchArtifactsPerAction - 1);
        contradictionCount = between(contradictionCount, 0, 6);
        initialTrust = between(initialTrust, 0, 100);
        initialInterest = between(initialInterest, 0, 100);
        initialPatience = between(initialPatience, 0, 100);
        meetingTurnLimit = between(meetingTurnLimit, 4, 20);
        timelinePressureDays = between(timelinePressureDays, 1, 90);
        requiredEvidenceCount = between(requiredEvidenceCount, 2, 8);
        requiredConfidencePercent = between(requiredConfidencePercent, 20, 90);
        outreachAcceptanceThreshold = between(outreachAcceptanceThreshold, 50, 95);
        proposalEvidenceCoverageThreshold = between(proposalEvidenceCoverageThreshold, 30, 95);
        personaResistance = between(personaResistance, 0, 100);
        scoringTolerance = between(scoringTolerance, 70, 130);
    }

    public static DifficultyProfile defaults(int overallDifficulty, int ambiguity, int stakeholderComplexity,
                                             int commercialPressure) {
        DifficultyLevel level = overallDifficulty <= 2 ? DifficultyLevel.EASY
                : overallDifficulty >= 4 ? DifficultyLevel.HARD : DifficultyLevel.MEDIUM;
        return switch (level) {
            case EASY -> new DifficultyProfile(level, 4, 1, 0, 10, 10, 10, 14,
                    true, 30, 2, 40, 65, 50, 20, 115);
            case MEDIUM -> new DifficultyProfile(level, 5, 2, 1, 10, 10, 10, 14,
                    false, 18, 3, 60, 75, 65, 50, 100);
            case HARD -> new DifficultyProfile(level, 6, 3, Math.max(2, ambiguity - 1), 10, 10, 10, 12,
                    false, commercialPressure >= 4 ? 10 : 14, 4, 80, 82, 75,
                    Math.max(65, stakeholderComplexity * 15), 85);
        };
    }

    private static int between(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
