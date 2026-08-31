package com.ibm.consulting.sim.scenario.infrastructure;

import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.PersonaRepository;
import com.ibm.consulting.sim.scenario.domain.AdminScenarioCatalogQuery;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioCatalogPage;
import com.ibm.consulting.sim.scenario.domain.ScenarioCatalogQuery;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataScenarioRepository extends JpaRepository<Scenario, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Scenario> {
    List<Scenario> findByStatus(ScenarioStatus status);
    Optional<Scenario> findByIdAndStatus(
            UUID id,
            ScenarioStatus status
    );
    List<Scenario> findByScenarioLineageIdAndStatus(UUID scenarioLineageId, ScenarioStatus status);

    @Query("""
            select distinct scenario.industry from Scenario scenario
            where scenario.status = :status
            order by scenario.industry asc
            """)
    List<String> findDistinctIndustries(@Param("status") ScenarioStatus status);
}

@Repository
class JpaScenarioRepository implements ScenarioRepository {

    private final SpringDataScenarioRepository repo;

    JpaScenarioRepository(SpringDataScenarioRepository repo) {
        this.repo = repo;
    }

    @Override public List<Scenario> findAllActive() { return repo.findByStatus(ScenarioStatus.ACTIVE); }
    @Override public ScenarioCatalogPage findCatalog(ScenarioCatalogQuery query) {
        var page = repo.findAll(catalogueSpecification(query), PageRequest.of(
                query.page(), query.size(), Sort.by(Sort.Direction.ASC, "title")));
        return new ScenarioCatalogPage(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
    }
    @Override public ScenarioCatalogPage findAdminCatalog(AdminScenarioCatalogQuery query) {
        var page = repo.findAll(adminCatalogueSpecification(query), PageRequest.of(
                query.page(), query.size(), Sort.by(Sort.Direction.DESC, "updatedAt")));
        return new ScenarioCatalogPage(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
    }
    @Override public List<String> findCatalogIndustries() { return repo.findDistinctIndustries(ScenarioStatus.ACTIVE); }
    @Override public List<Scenario> findAll() { return repo.findAll(); }
    @Override public List<Scenario> findByLineageIdAndStatus(UUID lineageId, ScenarioStatus status) {
        return repo.findByScenarioLineageIdAndStatus(lineageId, status);
    }
    @Override public Optional<Scenario> findById(UUID id) { return repo.findById(id); }

    /**
     * @param id
     * @param status
     * @return
     */
    @Override
    public Optional<Scenario> findByIdAndStatus(UUID id, ScenarioStatus status) {
        return repo.findByIdAndStatus(id,status);
    }

    @Override public List<Scenario> findByIdIn(List<UUID> ids) { return ids.isEmpty() ? List.of() : repo.findAllById(ids); }
    @Override public Scenario save(Scenario scenario) { return repo.save(scenario); }

    private Specification<Scenario> catalogueSpecification(ScenarioCatalogQuery query) {
        return (root, criteriaQuery, builder) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("status"), ScenarioStatus.ACTIVE));

            if (query.industry() != null) {
                predicates.add(builder.equal(builder.lower(root.get("industry")), query.industry()));
            }
            if (query.difficulty() != null) {
                predicates.add(builder.equal(root.get("difficulty"), query.difficulty()));
            }
            if (query.search() != null) {
                String pattern = "%" + query.search() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("industry")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Scenario> adminCatalogueSpecification(AdminScenarioCatalogQuery query) {
        return (root, criteriaQuery, builder) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (query.search() != null) {
                String pattern = "%" + query.search() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("industry")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}

@Repository
interface SpringDataPersonaRepository extends JpaRepository<Persona, UUID> {
}

@Repository
class JpaPersonaRepository implements PersonaRepository {

    private final SpringDataPersonaRepository repo;

    JpaPersonaRepository(SpringDataPersonaRepository repo) {
        this.repo = repo;
    }

    @Override public Optional<Persona> findById(UUID id) { return repo.findById(id); }
}
