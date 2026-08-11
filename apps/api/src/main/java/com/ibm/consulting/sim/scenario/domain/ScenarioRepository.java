package com.ibm.consulting.sim.scenario.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioRepository {
    List<Scenario> findAllActive();
    List<Scenario> findAll();
    List<Scenario> findByLineageIdAndStatus(UUID lineageId, ScenarioStatus status);
    Optional<Scenario> findById(UUID id);
    Scenario save(Scenario scenario);
}
