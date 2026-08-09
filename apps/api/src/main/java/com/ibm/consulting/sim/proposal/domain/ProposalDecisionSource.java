package com.ibm.consulting.sim.proposal.domain;

/** Read-only source projection used by the deterministic decision engine. */
public record ProposalDecisionSource(String id, String content, String type) {}
