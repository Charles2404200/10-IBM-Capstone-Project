package com.ibm.consulting.sim.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentEngineTest {

    @Test
    void producesFourCompetencyScores() {
        List<CompetencyScore> scores = AssessmentEngine.score(3, 70, 60, 65, 55, 75);
        assertThat(scores).hasSize(4);
        assertThat(scores).extracting(CompetencyScore::getCompetencyName)
                .containsExactly("Research & Discovery", "Outreach Effectiveness",
                        "Relationship Building", "Solution Alignment");
    }

    @Test
    void discoveryScoreCapsAtOneHundredWithSufficientEvidence() {
        List<CompetencyScore> scores = AssessmentEngine.score(10, 50, 50, 50, 50, 50);
        CompetencyScore discovery = scores.get(0);
        assertThat(discovery.getScore()).isEqualTo(100);
    }

    @Test
    void discoveryScoreIsZeroWithNoEvidence() {
        List<CompetencyScore> scores = AssessmentEngine.score(0, 50, 50, 50, 50, 50);
        assertThat(scores.get(0).getScore()).isZero();
    }

    @Test
    void relationshipScoreIsAverageOfTrustInterestPatience() {
        List<CompetencyScore> scores = AssessmentEngine.score(3, 50, 90, 60, 30, 50);
        CompetencyScore relationship = scores.get(2);
        assertThat(relationship.getScore()).isEqualTo(60); // (90+60+30)/3
    }

    @Test
    void overallIsAverageOfAllCompetencyScores() {
        List<CompetencyScore> scores = AssessmentEngine.score(5, 100, 100, 100, 100, 100);
        assertThat(AssessmentEngine.overall(scores)).isEqualTo(100);
    }

    @Test
    void overallIsZeroForEmptyScoreList() {
        assertThat(AssessmentEngine.overall(List.of())).isZero();
    }
}
