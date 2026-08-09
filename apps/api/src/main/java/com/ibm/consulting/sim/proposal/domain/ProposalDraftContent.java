package com.ibm.consulting.sim.proposal.domain;

import java.math.BigDecimal;
import java.util.List;

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
        components = List.copyOf(components == null ? List.of() : components);
        budget = budget == null ? BigDecimal.ZERO : budget;
        timelineWeeks = Math.max(1, timelineWeeks);
        budgetConfidence = budgetConfidence == null || budgetConfidence.isBlank() ? "UNCONFIRMED" : budgetConfidence;
        budgetSource = budgetSource == null ? "" : budgetSource.trim();
        businessOutcomes = List.copyOf(businessOutcomes == null ? List.of() : businessOutcomes);
        milestones = List.copyOf(milestones == null ? List.of() : milestones);
        risks = List.copyOf(risks == null ? List.of() : risks);
        assumptions = List.copyOf(assumptions == null ? List.of() : assumptions);
        evidenceLinks = List.copyOf(evidenceLinks == null ? List.of() : evidenceLinks);
    }
}
