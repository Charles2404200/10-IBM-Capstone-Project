package com.ibm.consulting.sim.achievement.application;

import com.ibm.consulting.sim.achievement.domain.ConditionType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Transport-boundary validator for a single achievement rule node. Child nodes are cascaded by Bean Validation. */
public final class ConditionNodeValidator implements ConstraintValidator<ValidConditionNode, ConditionNode> {

    @Override
    public boolean isValid(ConditionNode node, ConstraintValidatorContext context) {
        if (node == null) return true;
        if (node.kind() == null) return false;

        return switch (node.kind()) {
            case GROUP -> node.operator() != null
                    && node.children() != null
                    && !node.children().isEmpty()
                    && node.type() == null
                    && node.competencyName() == null
                    && node.threshold() == null;
            case LEAF -> node.operator() == null
                    && node.children() == null
                    && node.type() != null
                    && validThreshold(node.type(), node.threshold())
                    && validCompetency(node.type(), node.competencyName());
        };
    }

    private static boolean validThreshold(ConditionType type, Double threshold) {
        if (threshold == null || !Double.isFinite(threshold) || threshold < 0) return false;
        return !type.hasPercentageThreshold() || threshold <= 100;
    }

    private static boolean validCompetency(ConditionType type, String competencyName) {
        return type != ConditionType.MIN_COMPETENCY_SCORE
                || (competencyName != null && !competencyName.isBlank());
    }
}
