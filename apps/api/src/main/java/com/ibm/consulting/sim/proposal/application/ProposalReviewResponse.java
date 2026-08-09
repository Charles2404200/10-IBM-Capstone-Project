package com.ibm.consulting.sim.proposal.application;

import java.util.List;

public record ProposalReviewResponse(
        boolean readyToSubmit,
        List<ProposalValidationIssue> validationIssues,
        List<ClientAlignmentItem> clientAlignment,
        int problemDefinitionScore,
        int evidenceGroundingScore,
        int clientAlignmentScore,
        int commercialLogicScore,
        int riskCoverageScore,
        int feasibilityScore,
        String executiveFeedback,
        List<String> improvementActions) {}
