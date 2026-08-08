package com.ibm.consulting.sim.proposal.infrastructure;

import com.ibm.consulting.sim.proposal.domain.Proposal;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataProposalRepository extends JpaRepository<Proposal, UUID> {
    Optional<Proposal> findByEngagementId(UUID engagementId);
}

@Repository
class JpaProposalRepository implements ProposalRepository {

    private final SpringDataProposalRepository repo;

    JpaProposalRepository(SpringDataProposalRepository repo) {
        this.repo = repo;
    }

    @Override public Proposal save(Proposal proposal) { return repo.save(proposal); }
    @Override public Optional<Proposal> findByEngagementId(UUID engagementId) {
        return repo.findByEngagementId(engagementId);
    }
}
