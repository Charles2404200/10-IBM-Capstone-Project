package com.ibm.consulting.sim.proposal.application;

import java.util.List;

public record ProposalWorkspaceResponse(ProposalResponse proposal, List<ProposalSource> sources) {}
