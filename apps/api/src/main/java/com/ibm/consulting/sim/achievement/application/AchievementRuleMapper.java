package com.ibm.consulting.sim.achievement.application;

import com.ibm.consulting.sim.achievement.domain.AchievementCondition;
import com.ibm.consulting.sim.achievement.domain.ConditionGroup;
import com.ibm.consulting.sim.achievement.domain.LeafCondition;
import org.springframework.stereotype.Component;

import java.util.List;

/** Converts between the wire/persistence {@link ConditionNode} DTO and the domain rule tree. */
@Component
public class AchievementRuleMapper {

    public AchievementCondition toDomain(ConditionNode node) {
        if (node.kind() == ConditionNode.Kind.GROUP) {
            List<AchievementCondition> children = node.children().stream().map(this::toDomain).toList();
            return new ConditionGroup(node.operator(), children);
        }
        return new LeafCondition(node.type(), node.competencyName(), node.threshold());
    }

    public ConditionNode toDto(AchievementCondition condition) {
        if (condition instanceof ConditionGroup group) {
            return ConditionNode.group(group.operator(), group.children().stream().map(this::toDto).toList());
        }
        LeafCondition leaf = (LeafCondition) condition;
        return ConditionNode.leaf(leaf.type(), leaf.competencyName(), leaf.threshold());
    }
}
