package com.ibm.consulting.sim.proposal.application;

import com.ibm.consulting.sim.proposal.domain.Proposal;
import com.ibm.consulting.sim.proposal.domain.ClientDecisionOutcome;

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
        String clientDecisionOutcome,
        int decisionConfidence,
        int learnerPerformanceScore,
        List<ProposalDecisionDimensionResponse> decisionDimensions,
        List<ProposalDecisionInsightResponse> decisionInsights,
        List<ProposalEvidenceImpactResponse> evidenceImpacts,
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
                p.getDecision().name(), p.getDecisionRationale(), p.getClientResponse(),
                outcome(p).name(), valueOr(p.getDecisionConfidence(), p.getAlignmentScore()),
                valueOr(p.getLearnerPerformanceScore(), p.getAlignmentScore()),
                dimensions(p), insights(p), impacts(p), p.getSubmittedAt());
    }

    private static ClientDecisionOutcome outcome(Proposal proposal) {
        if (proposal.getClientDecisionOutcome() != null) return proposal.getClientDecisionOutcome();
        return proposal.getDecision() != null && proposal.getDecision().name().equals("WON")
                ? ClientDecisionOutcome.PROPOSAL_ACCEPTED : ClientDecisionOutcome.REJECTED;
    }
    private static int valueOr(Integer value, int fallback) { return value == null ? fallback : value; }
    private static List<ProposalDecisionDimensionResponse> dimensions(Proposal proposal) {
        List<ProposalDecisionDimensionResponse> dimensions = proposal.getDecisionDimensions().stream()
                .map(item -> new ProposalDecisionDimensionResponse(item.getDimension(), item.getScore(), item.getInterpretation())).toList();
        return dimensions.isEmpty() ? List.of(new ProposalDecisionDimensionResponse("Legacy decision score", proposal.getAlignmentScore(),
                "This proposal was submitted before the expanded decision engine.")) : dimensions;
    }
    private static List<ProposalDecisionInsightResponse> insights(Proposal proposal) {
        List<ProposalDecisionInsightResponse> insights = proposal.getDecisionInsights().stream()
                .map(item -> new ProposalDecisionInsightResponse(item.getCategory(), item.getDetail())).toList();
        return insights.isEmpty() ? List.of(new ProposalDecisionInsightResponse("CONCERN",
                "Detailed decision factors were not stored for this legacy proposal.")) : insights;
    }
    private static List<ProposalEvidenceImpactResponse> impacts(Proposal proposal) {
        return proposal.getEvidenceImpacts().stream()
                .map(item -> new ProposalEvidenceImpactResponse(item.getClaim(), item.getSupportLevel(), item.getExplanation())).toList();
    }

    public record ProposalOutcomeResponse(String outcome, String metric, String target) {}
    public record ProposalMilestoneResponse(String phase, String duration) {}
    public record ProposalRiskResponse(String risk, String severity, String mitigation) {}
    public record ProposalEvidenceLinkResponse(String section, String sourceId) {}
    public record ProposalDecisionDimensionResponse(String dimension, int score, String interpretation) {}
    public record ProposalDecisionInsightResponse(String category, String detail) {}
    public record ProposalEvidenceImpactResponse(String claim, String supportLevel, String explanation) {}
}
