package com.ibm.consulting.sim.achievement.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementRuleEvaluatorTest {

    private static final AchievementFactSheet FACTS = new AchievementFactSheet(
            5, 3, 88, 72.5, 2, 60.0, Map.of("Relationship Building", 85, "Research & Discovery", 70));

    @Test
    void leafConditionIsSatisfiedWhenActualMeetsThreshold() {
        LeafCondition condition = new LeafCondition(ConditionType.MIN_ENGAGEMENTS_WON, null, 3);
        assertThat(AchievementRuleEvaluator.isSatisfied(condition, FACTS)).isTrue();
    }

    @Test
    void leafConditionIsNotSatisfiedWhenBelowThreshold() {
        LeafCondition condition = new LeafCondition(ConditionType.MIN_ENGAGEMENTS_WON, null, 10);
        assertThat(AchievementRuleEvaluator.isSatisfied(condition, FACTS)).isFalse();
        assertThat(AchievementRuleEvaluator.progress(condition, FACTS)).isEqualTo(30.0);
    }

    @Test
    void competencyLeafReadsBestScoreForNamedCompetency() {
        LeafCondition condition = new LeafCondition(ConditionType.MIN_COMPETENCY_SCORE, "Relationship Building", 80);
        assertThat(AchievementRuleEvaluator.isSatisfied(condition, FACTS)).isTrue();
    }

    @Test
    void andGroupRequiresAllChildrenSatisfied() {
        ConditionGroup group = new ConditionGroup(LogicalOperator.AND, List.of(
                new LeafCondition(ConditionType.MIN_ENGAGEMENTS_WON, null, 3),
                new LeafCondition(ConditionType.MIN_BEST_OVERALL_SCORE, null, 90)));

        assertThat(AchievementRuleEvaluator.isSatisfied(group, FACTS)).isFalse();
        // progress is the minimum of children: won=100%, bestScore=88/90*100
        assertThat(AchievementRuleEvaluator.progress(group, FACTS)).isCloseTo(97.77, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void orGroupIsSatisfiedIfAnyChildSatisfied() {
        ConditionGroup group = new ConditionGroup(LogicalOperator.OR, List.of(
                new LeafCondition(ConditionType.MIN_ENGAGEMENTS_WON, null, 100),
                new LeafCondition(ConditionType.MIN_BEST_OVERALL_SCORE, null, 80)));

        assertThat(AchievementRuleEvaluator.isSatisfied(group, FACTS)).isTrue();
    }

    @Test
    void nestedGroupsCombineCorrectly() {
        ConditionGroup nested = new ConditionGroup(LogicalOperator.OR, List.of(
                new ConditionGroup(LogicalOperator.AND, List.of(
                        new LeafCondition(ConditionType.MIN_ENGAGEMENTS_WON, null, 3),
                        new LeafCondition(ConditionType.MIN_AVERAGE_OVERALL_SCORE, null, 70))),
                new LeafCondition(ConditionType.MIN_ENGAGEMENTS_WON, null, 100)));

        assertThat(AchievementRuleEvaluator.isSatisfied(nested, FACTS)).isTrue();
    }

    @Test
    void leafConditionWithZeroThresholdIsAlwaysSatisfied() {
        LeafCondition condition = new LeafCondition(ConditionType.MIN_ENGAGEMENTS_COMPLETED, null, 0);
        assertThat(AchievementRuleEvaluator.isSatisfied(condition, AchievementFactSheet.empty())).isTrue();
    }

    @Test
    void conditionGroupRejectsEmptyChildren() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ConditionGroup(LogicalOperator.AND, List.of()));
    }

    @Test
    void competencyLeafRequiresCompetencyName() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new LeafCondition(ConditionType.MIN_COMPETENCY_SCORE, null, 80));
    }
}
