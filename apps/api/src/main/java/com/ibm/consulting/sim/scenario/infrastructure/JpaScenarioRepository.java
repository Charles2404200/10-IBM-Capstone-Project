package com.ibm.consulting.sim.scenario.infrastructure;

import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.PersonaRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataScenarioRepository extends JpaRepository<Scenario, UUID> {
    List<Scenario> findByStatus(ScenarioStatus status);
    List<Scenario> findByScenarioLineageIdAndStatus(UUID scenarioLineageId, ScenarioStatus status);
}

@Repository
class JpaScenarioRepository implements ScenarioRepository {

    private final SpringDataScenarioRepository repo;

    JpaScenarioRepository(SpringDataScenarioRepository repo) {
        this.repo = repo;
    }

    @Override public List<Scenario> findAllActive() { return repo.findByStatus(ScenarioStatus.ACTIVE); }
    @Override public List<Scenario> findAll() { return repo.findAll(); }
    @Override public List<Scenario> findByLineageIdAndStatus(UUID lineageId, ScenarioStatus status) {
        return repo.findByScenarioLineageIdAndStatus(lineageId, status);
    }
    @Override public Optional<Scenario> findById(UUID id) { return repo.findById(id); }
    @Override public Scenario save(Scenario scenario) { return repo.save(scenario); }
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
