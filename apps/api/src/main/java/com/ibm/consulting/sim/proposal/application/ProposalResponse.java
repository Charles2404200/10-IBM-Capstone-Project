package com.ibm.consulting.sim.proposal.application;

import com.ibm.consulting.sim.proposal.domain.Proposal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProposalResponse(
        UUID id,
        UUID engagementId,
        String status,
        String problemStatement,
        String solutionStrategy,
        List<String> components,
        BigDecimal budget,
        int timelineWeeks,
        String budgetConfidence,
        String budgetSource,
        List<ProposalOutcomeResponse> businessOutcomes,
        List<ProposalMilestoneResponse> milestones,
        List<ProposalRiskResponse> risks,
        List<String> assumptions,
        List<ProposalEvidenceLinkResponse> evidenceLinks,
        int alignmentScore,
        String decision,
        String decisionRationale,
        String clientResponse,
        Instant submittedAt) {

    public static ProposalResponse from(Proposal p) {
        return new ProposalResponse(p.getId(), p.getEngagementId(), p.getStatus().name(), p.getProblemStatement(),
                p.getSolutionStrategy(), List.copyOf(p.getComponents()), p.getBudget(), p.getTimelineWeeks(),
                p.getBudgetConfidence(), p.getBudgetSource(),
                p.getBusinessOutcomes().stream().map(item -> new ProposalOutcomeResponse(item.getOutcome(), item.getMetric(), item.getTarget())).toList(),
                p.getMilestones().stream().map(item -> new ProposalMilestoneResponse(item.getPhase(), item.getDuration())).toList(),
                p.getRisks().stream().map(item -> new ProposalRiskResponse(item.getRisk(), item.getSeverity(), item.getMitigation())).toList(),
                List.copyOf(p.getAssumptions()),
                p.getEvidenceLinks().stream().map(item -> new ProposalEvidenceLinkResponse(item.getSection(), item.getSourceId())).toList(),
                p.getAlignmentScore(),
                p.getDecision().name(), p.getDecisionRationale(), p.getClientResponse(), p.getSubmittedAt());
    }

    public record ProposalOutcomeResponse(String outcome, String metric, String target) {}
    public record ProposalMilestoneResponse(String phase, String duration) {}
    public record ProposalRiskResponse(String risk, String severity, String mitigation) {}
    public record ProposalEvidenceLinkResponse(String section, String sourceId) {}
}
