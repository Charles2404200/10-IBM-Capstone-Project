package com.ibm.consulting.sim.proposal.application;

import com.ibm.consulting.sim.proposal.domain.Proposal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProposalResponse(
        UUID id,
        UUID engagementId,
        String problemStatement,
        List<String> components,
        BigDecimal budget,
        int timelineWeeks,
        int alignmentScore,
        String decision,
        String decisionRationale,
        Instant submittedAt) {

    public static ProposalResponse from(Proposal p) {
        return new ProposalResponse(p.getId(), p.getEngagementId(), p.getProblemStatement(),
                p.getComponents(), p.getBudget(), p.getTimelineWeeks(), p.getAlignmentScore(),
                p.getDecision().name(), p.getDecisionRationale(), p.getSubmittedAt());
    }
}
