package com.ibm.consulting.sim.assessment.infrastructure;

import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataAssessmentRepository extends JpaRepository<Assessment, UUID> {
    Optional<Assessment> findByEngagementId(UUID engagementId);
    List<Assessment> findByEngagementIdIn(List<UUID> engagementIds);
}

@Repository
class JpaAssessmentRepository implements AssessmentRepository {

    private final SpringDataAssessmentRepository repo;

    JpaAssessmentRepository(SpringDataAssessmentRepository repo) {
        this.repo = repo;
    }

    @Override public Assessment save(Assessment assessment) { return repo.save(assessment); }
    @Override public Optional<Assessment> findByEngagementId(UUID engagementId) {
        return repo.findByEngagementId(engagementId);
    }
    @Override public List<Assessment> findAllByEngagementIdIn(List<UUID> engagementIds) {
        return repo.findByEngagementIdIn(engagementIds);
    }
}
