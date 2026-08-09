package com.ibm.consulting.sim.proposal.application;

public record ProposalValidationIssue(String severity, String code, String message, String section) {}
