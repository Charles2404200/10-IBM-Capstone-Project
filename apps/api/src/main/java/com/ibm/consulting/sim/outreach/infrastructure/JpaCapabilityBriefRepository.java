package com.ibm.consulting.sim.outreach.infrastructure;

import com.ibm.consulting.sim.outreach.domain.CapabilityBrief;
import com.ibm.consulting.sim.outreach.domain.CapabilityBriefRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataCapabilityBriefRepository extends JpaRepository<CapabilityBrief, UUID> {
    Optional<CapabilityBrief> findByEngagementId(UUID engagementId);
}

@Repository
class JpaCapabilityBriefRepository implements CapabilityBriefRepository {
    private final SpringDataCapabilityBriefRepository repository;

    JpaCapabilityBriefRepository(SpringDataCapabilityBriefRepository repository) {
        this.repository = repository;
    }

    @Override public CapabilityBrief save(CapabilityBrief brief) { return repository.save(brief); }
    @Override public Optional<CapabilityBrief> findByEngagementId(UUID engagementId) { return repository.findByEngagementId(engagementId); }
}
