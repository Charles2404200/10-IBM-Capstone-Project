package com.ibm.consulting.sim.scenario.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioRepository {
    List<Scenario> findAllActive();
    ScenarioCatalogPage findCatalog(ScenarioCatalogQuery query);
    ScenarioCatalogPage findAdminCatalog(AdminScenarioCatalogQuery query);
    List<String> findCatalogIndustries();
    List<Scenario> findAll();
    List<Scenario> findByLineageIdAndStatus(UUID lineageId, ScenarioStatus status);
    Optional<Scenario> findById(UUID id);
    Optional<Scenario> findByIdAndStatus(UUID id,ScenarioStatus status);
    List<Scenario> findByIdIn(List<UUID> ids);
    Scenario save(Scenario scenario);
}
