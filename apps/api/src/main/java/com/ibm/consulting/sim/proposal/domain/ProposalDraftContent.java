package com.ibm.consulting.sim.proposal.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable command value used to update a learner-owned proposal draft. */
public record ProposalDraftContent(
        String problemStatement,
        String solutionStrategy,
        List<String> components,
        BigDecimal budget,
        int timelineWeeks,
        String budgetConfidence,
        String budgetSource,
        List<ProposalBusinessOutcome> businessOutcomes,
        List<ProposalMilestone> milestones,
        List<ProposalRisk> risks,
        List<String> assumptions,
        List<ProposalEvidenceLink> evidenceLinks) {

    public ProposalDraftContent {
        problemStatement = problemStatement == null ? "" : problemStatement.trim();
        solutionStrategy = solutionStrategy == null ? "" : solutionStrategy.trim();
        components = normalisedText(components);
        budget = budget == null ? BigDecimal.ZERO : budget;
        timelineWeeks = Math.max(1, timelineWeeks);
        budgetConfidence = budgetConfidence == null || budgetConfidence.isBlank() ? "UNCONFIRMED" : budgetConfidence;
        budgetSource = budgetSource == null ? "" : budgetSource.trim();
        businessOutcomes = nonNull(businessOutcomes);
        milestones = nonNull(milestones);
        risks = nonNull(risks);
        assumptions = normalisedText(assumptions);
        evidenceLinks = nonNull(evidenceLinks);
    }

    private static List<String> normalisedText(List<String> values) {
        return nonNull(values).stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static <T> List<T> nonNull(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
