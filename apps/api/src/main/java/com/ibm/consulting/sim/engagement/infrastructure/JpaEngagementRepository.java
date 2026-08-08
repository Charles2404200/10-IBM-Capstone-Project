package com.ibm.consulting.sim.engagement.infrastructure;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataEngagementRepository extends JpaRepository<Engagement, UUID> {
    List<Engagement> findByUserId(UUID userId);
    Optional<Engagement> findByIdAndUserId(UUID id, UUID userId);
}

@Repository
class JpaEngagementRepository implements EngagementRepository {

    private final SpringDataEngagementRepository repo;

    JpaEngagementRepository(SpringDataEngagementRepository repo) {
        this.repo = repo;
    }

    @Override public Engagement save(Engagement e) { return repo.save(e); }
    @Override public Optional<Engagement> findById(UUID id) { return repo.findById(id); }
    @Override public List<Engagement> findByUserId(UUID userId) { return repo.findByUserId(userId); }
    @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId);
    }
}
