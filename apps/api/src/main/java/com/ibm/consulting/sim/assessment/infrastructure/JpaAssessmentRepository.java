package com.ibm.consulting.sim.assessment.infrastructure;

import com.ibm.consulting.sim.assessment.domain.Assessment;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import org.springframework.cache.CacheManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ibm.consulting.sim.shared.config.CacheConfig.ADMIN_PLATFORM_OVERVIEW_CACHE;

@Repository
interface SpringDataAssessmentRepository extends JpaRepository<Assessment, UUID> {
    Optional<Assessment> findByEngagementId(UUID engagementId);
    List<Assessment> findByEngagementIdIn(List<UUID> engagementIds);
}

@Repository
class JpaAssessmentRepository implements AssessmentRepository {

    private final SpringDataAssessmentRepository repo;
    private final CacheManager cacheManager;

    JpaAssessmentRepository(SpringDataAssessmentRepository repo, CacheManager cacheManager) {
        this.repo = repo;
        this.cacheManager = cacheManager;
    }

    @Override
    public Assessment save(Assessment assessment) {
        Assessment saved = repo.save(assessment);
        evictPlatformOverviewAfterCommit();
        return saved;
    }
    @Override public Optional<Assessment> findByEngagementId(UUID engagementId) {
        return repo.findByEngagementId(engagementId);
    }
    @Override public List<Assessment> findAllByEngagementIdIn(List<UUID> engagementIds) {
        return repo.findByEngagementIdIn(engagementIds);
    }

    private void evictPlatformOverviewAfterCommit() {
        Runnable eviction = () -> cacheManager.getCache(ADMIN_PLATFORM_OVERVIEW_CACHE).evict("global");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eviction.run();
            }
        });
    }
}
