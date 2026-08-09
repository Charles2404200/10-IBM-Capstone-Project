package com.ibm.consulting.sim.proposal.application;

/** A traceable fact available to the learner while drafting a proposal. */
public record ProposalSource(String id, String label, String type, String content, String reliability) {}
