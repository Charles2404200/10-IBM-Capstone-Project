package com.ibm.consulting.sim.outreach.infrastructure;

import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataOutreachRepository extends JpaRepository<OutreachAttempt, UUID> {
    List<OutreachAttempt> findByEngagementIdOrderByAttemptNumberAsc(UUID engagementId);
    int countByEngagementId(UUID engagementId);
}

@Repository
class JpaOutreachRepository implements OutreachRepository {
    private final SpringDataOutreachRepository repo;
    JpaOutreachRepository(SpringDataOutreachRepository repo) { this.repo = repo; }

    @Override public OutreachAttempt save(OutreachAttempt a) { return repo.save(a); }
    @Override public List<OutreachAttempt> findByEngagementId(UUID id) {
        return repo.findByEngagementIdOrderByAttemptNumberAsc(id);
    }
    @Override public Optional<OutreachAttempt> findById(UUID id) { return repo.findById(id); }
    @Override public int countByEngagementId(UUID id) { return repo.countByEngagementId(id); }
}
