package com.ibm.consulting.sim.proposal.domain;

import java.util.Optional;
import java.util.UUID;

public interface ProposalRepository {
    Proposal save(Proposal proposal);
    Optional<Proposal> findByEngagementId(UUID engagementId);
}
