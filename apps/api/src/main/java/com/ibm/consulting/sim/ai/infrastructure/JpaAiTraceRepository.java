package com.ibm.consulting.sim.ai.infrastructure;

import com.ibm.consulting.sim.ai.domain.AiTrace;
import com.ibm.consulting.sim.ai.domain.AiTraceRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
interface SpringDataAiTraceRepository extends JpaRepository<AiTrace, UUID> {
    List<AiTrace> findByEngagementIdOrderByCreatedAtAsc(UUID engagementId);
}

@Repository
class JpaAiTraceRepository implements AiTraceRepository {

    private final SpringDataAiTraceRepository repo;

    JpaAiTraceRepository(SpringDataAiTraceRepository repo) {
        this.repo = repo;
    }

    @Override
    public AiTrace save(AiTrace trace) {
        return repo.save(trace);
    }

    @Override
    public List<AiTrace> findByEngagementId(UUID engagementId) {
        return repo.findByEngagementIdOrderByCreatedAtAsc(engagementId);
    }
}
