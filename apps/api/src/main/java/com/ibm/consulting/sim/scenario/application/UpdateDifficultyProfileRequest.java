package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibm.consulting.sim.scenario.domain.DifficultyLevel;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Validated HTTP command kept separate from the defensive, clamping domain value object. */
public record UpdateDifficultyProfileRequest(@NotNull @Valid Profile profile) {

    public DifficultyProfile toDomain() {
        return profile.toDomain();
    }

    public record Profile(
            @NotNull DifficultyLevel level,
            @NotNull @Min(2) @Max(8) Integer researchArtifactsPerAction,
            @NotNull @Min(0) @Max(7) Integer distractorArtifactsPerAction,
            @NotNull @Min(0) @Max(6) Integer contradictionCount,
            @NotNull @Min(0) @Max(100) Integer initialTrust,
            @NotNull @Min(0) @Max(100) Integer initialInterest,
            @NotNull @Min(0) @Max(100) Integer initialPatience,
            @NotNull @Min(4) @Max(20) Integer meetingTurnLimit,
            @NotNull Boolean budgetVisible,
            @NotNull @Min(1) @Max(90) Integer timelinePressureDays,
            @NotNull @Min(2) @Max(8) Integer requiredEvidenceCount,
            @NotNull @Min(20) @Max(90) Integer requiredConfidencePercent,
            @NotNull @Min(50) @Max(95) Integer outreachAcceptanceThreshold,
            @NotNull @Min(30) @Max(95) Integer proposalEvidenceCoverageThreshold,
            @NotNull @Min(0) @Max(100) Integer personaResistance,
            @NotNull @Min(70) @Max(130) Integer scoringTolerance) {

        @AssertTrue(message = "distractorArtifactsPerAction must be less than researchArtifactsPerAction")
        @JsonIgnore
        public boolean isDistractorCountValid() {
            return researchArtifactsPerAction == null || distractorArtifactsPerAction == null
                    || distractorArtifactsPerAction < researchArtifactsPerAction;
        }

        DifficultyProfile toDomain() {
            return new DifficultyProfile(level, researchArtifactsPerAction, distractorArtifactsPerAction,
                    contradictionCount, initialTrust, initialInterest, initialPatience, meetingTurnLimit,
                    budgetVisible, timelinePressureDays, requiredEvidenceCount, requiredConfidencePercent,
                    outreachAcceptanceThreshold, proposalEvidenceCoverageThreshold, personaResistance,
                    scoringTolerance);
        }
    }
}
