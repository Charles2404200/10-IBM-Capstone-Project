package com.ibm.consulting.sim.engagement.infrastructure;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import org.springframework.cache.CacheManager;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataEngagementRepository extends JpaRepository<Engagement, UUID> {
    List<Engagement> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "events")
    @Query("select distinct engagement from Engagement engagement where engagement.userId = :userId order by engagement.updatedAt desc")
    List<Engagement> findDashboardByUserId(UUID userId);

    Optional<Engagement> findByIdAndUserId(UUID id, UUID userId);
}

@Repository
class JpaEngagementRepository implements EngagementRepository {

    private final SpringDataEngagementRepository repo;
    private final CacheManager cacheManager;

    JpaEngagementRepository(SpringDataEngagementRepository repo, CacheManager cacheManager) {
        this.repo = repo;
        this.cacheManager = cacheManager;
    }

    /**
     * Cache invalidation belongs at the persistence boundary, covering every
     * lifecycle command. Deferring it until after commit prevents another
     * request from repopulating a cache entry from an uncommitted transaction.
     */
    @Override
    public Engagement save(Engagement engagement) {
        Engagement saved = repo.save(engagement);
        evictLearnerReadModelsAfterCommit(saved.getUserId());
        return saved;
    }
    @Override public List<Engagement> findAll() { return repo.findAll(); }
    @Override public Optional<Engagement> findById(UUID id) { return repo.findById(id); }
    @Override public List<Engagement> findByUserId(UUID userId) { return repo.findByUserId(userId); }
    @Override public List<Engagement> findDashboardByUserId(UUID userId) { return repo.findDashboardByUserId(userId); }
    @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId);
    }

    private void evictLearnerReadModelsAfterCommit(UUID userId) {
        Runnable eviction = () -> {
            cacheManager.getCache("engagementDashboard").evict(userId);
            cacheManager.getCache("portfolioSummary").evict(userId);
        };
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
