package com.ibm.consulting.sim.proposal.application;

import java.util.List;

/** Qualitative coaching only. It never influences the deterministic outcome engine. */
public record ProposalReviewNarrative(String executiveFeedback, List<String> improvementActions) {
    public static ProposalReviewNarrative fallback(List<ProposalValidationIssue> issues) {
        String feedback = issues.isEmpty()
                ? "The proposal is structurally complete. Recheck each claim against the attached evidence before submission."
                : "Prioritise the validation findings before submission, especially any gaps in client evidence or delivery risk.";
        List<String> actions = issues.stream().limit(3).map(ProposalValidationIssue::message).toList();
        return new ProposalReviewNarrative(feedback,
                actions.isEmpty() ? List.of("Connect each important claim to a client source.") : actions);
    }
}
