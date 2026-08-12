package com.ibm.consulting.sim.lead.infrastructure;

import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadCatalogPage;
import com.ibm.consulting.sim.lead.domain.LeadCatalogQuery;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataLeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findByScenarioId(UUID scenarioId);

    @Query("""
            select lead from Lead lead
            where lead.scenarioId in (select scenario.id from Scenario scenario where scenario.status = :status)
              and (:scenarioId is null or lead.scenarioId = :scenarioId)
              and (:industry is null or lower(lead.industry) = :industry)
              and (:difficulty is null or lead.difficulty = :difficulty)
              and (:search is null or lower(lead.companyName) like concat('%', :search, '%')
                   or lower(lead.publicDescription) like concat('%', :search, '%'))
            order by lead.companyName asc
            """)
    Page<Lead> findCatalog(@Param("scenarioId") UUID scenarioId,
                           @Param("search") String search,
                           @Param("industry") String industry,
                           @Param("difficulty") LeadDifficulty difficulty,
                           @Param("status") ScenarioStatus status,
                           org.springframework.data.domain.Pageable pageable);

    @Query("""
            select distinct lead.industry from Lead lead
            where lead.scenarioId in (select scenario.id from Scenario scenario where scenario.status = :status)
            order by lead.industry asc
            """)
    List<String> findDistinctIndustries(@Param("status") ScenarioStatus status);
}

@Repository
class JpaLeadRepository implements LeadRepository {
    private final SpringDataLeadRepository repo;
    JpaLeadRepository(SpringDataLeadRepository repo) { this.repo = repo; }

    @Override public List<Lead> findByScenarioId(UUID scenarioId) { return repo.findByScenarioId(scenarioId); }
    @Override public LeadCatalogPage findCatalog(LeadCatalogQuery query) {
        Page<Lead> result = repo.findCatalog(query.scenarioId(), query.search(), query.industry(), query.difficulty(),
                ScenarioStatus.ACTIVE, PageRequest.of(query.page(), query.size()));
        return new LeadCatalogPage(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
    }
    @Override public List<String> findCatalogIndustries() { return repo.findDistinctIndustries(ScenarioStatus.ACTIVE); }
    @Override public Optional<Lead> findById(UUID id) { return repo.findById(id); }
    @Override public Lead save(Lead lead) { return repo.save(lead); }
    @Override public void delete(Lead lead) { repo.delete(lead); }
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
