package com.ibm.consulting.sim.lead.infrastructure;

import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataLeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findByScenarioId(UUID scenarioId);
}

@Repository
class JpaLeadRepository implements LeadRepository {
    private final SpringDataLeadRepository repo;
    JpaLeadRepository(SpringDataLeadRepository repo) { this.repo = repo; }

    @Override public List<Lead> findByScenarioId(UUID scenarioId) { return repo.findByScenarioId(scenarioId); }
    @Override public Optional<Lead> findById(UUID id) { return repo.findById(id); }
    @Override public Lead save(Lead lead) { return repo.save(lead); }
}

@Repository
interface SpringDataResearchRepo extends JpaRepository<ResearchEvidence, UUID> {
    List<ResearchEvidence> findByEngagementId(UUID engagementId);
    long countByEngagementId(UUID engagementId);
    List<ResearchEvidence> findByIdInAndEngagementId(List<UUID> ids, UUID engagementId);
}

@Repository
class JpaResearchEvidenceRepository implements ResearchEvidenceRepository {
    private final SpringDataResearchRepo repo;
    JpaResearchEvidenceRepository(SpringDataResearchRepo repo) { this.repo = repo; }

    @Override public ResearchEvidence save(ResearchEvidence e) { return repo.save(e); }
    @Override public List<ResearchEvidence> findByEngagementId(UUID engagementId) {
        return repo.findByEngagementId(engagementId);
    }
    @Override public long countByEngagementId(UUID engagementId) {
        return repo.countByEngagementId(engagementId);
    }
    @Override public List<ResearchEvidence> findByIdInAndEngagementId(List<UUID> ids, UUID engagementId) {
        return repo.findByIdInAndEngagementId(ids, engagementId);
    }
}
