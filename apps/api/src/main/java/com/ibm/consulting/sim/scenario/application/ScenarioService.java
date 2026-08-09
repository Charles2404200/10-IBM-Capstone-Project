package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ibm.consulting.sim.shared.config.CacheConfig.SCENARIOS_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.SCENARIO_CACHE;

@Service
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final DifficultyProfileService difficultyProfileService;

    public ScenarioService(ScenarioRepository scenarioRepository, DifficultyProfileService difficultyProfileService) {
        this.scenarioRepository = scenarioRepository;
        this.difficultyProfileService = difficultyProfileService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SCENARIOS_CACHE, key = "'active'")
    public List<ScenarioSummary> listActive() {
        return scenarioRepository.findAllActive().stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SCENARIO_CACHE, key = "#id")
    public ScenarioSummary getById(UUID id) {
        return scenarioRepository.findById(id)
                .map(this::summary)
                .orElseThrow(() -> new NotFoundException("Scenario", id));
    }

    /** Admin capability: list every scenario regardless of status (DRAFT/ACTIVE/ARCHIVED). */
    @Transactional(readOnly = true)
    public List<ScenarioSummary> listAllForAdmin() {
        return scenarioRepository.findAll().stream()
                .map(this::summary)
                .toList();
    }

    /** Author/admin capability: create a new scenario in DRAFT state. */
    @Transactional
    public ScenarioSummary create(CreateScenarioRequest request) {
        Scenario scenario = Scenario.create(
                request.title(), request.industry(), request.description(), request.difficulty());
        return summary(scenarioRepository.save(scenario));
    }

    /** Author/admin capability: attach a persona to an existing scenario. */
    @Transactional
    @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId")
    public ScenarioSummary addPersona(UUID scenarioId, CreatePersonaRequest request) {
        Scenario scenario = findScenario(scenarioId);
        Persona persona = scenario.addPersona(
                request.name(), request.jobTitle(), request.organisation(), request.communicationStyle(),
                request.visibleConcerns(), request.hiddenConcerns(), request.businessGoals());
        scenarioRepository.save(scenario);
        return summary(scenario);
    }

    /** Author/admin capability: publish a DRAFT scenario so it becomes visible to learners. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIOS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId")
    })
    public ScenarioSummary publish(UUID scenarioId) {
        Scenario scenario = findScenario(scenarioId);
        scenario.publish();
        return summary(scenarioRepository.save(scenario));
    }

    /** Author/admin capability: retire a scenario so it no longer appears for new engagements. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIOS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId")
    })
    public ScenarioSummary archive(UUID scenarioId) {
        Scenario scenario = findScenario(scenarioId);
        scenario.archive();
        return summary(scenarioRepository.save(scenario));
    }

    /** Author/admin capability: customise how much each competency contributes to the overall score. */
    @Transactional
    @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId")
    public ScenarioSummary updateRubricWeights(UUID scenarioId, Map<String, Integer> weights) {
        Scenario scenario = findScenario(scenarioId);
        scenario.updateRubricWeights(weights);
        return summary(scenarioRepository.save(scenario));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIOS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId")
    })
    public ScenarioSummary updateDifficultyProfile(UUID scenarioId, UpdateDifficultyProfileRequest request) {
        Scenario scenario = findScenario(scenarioId);
        difficultyProfileService.updateScenarioProfile(scenario, request.profile());
        return summary(scenarioRepository.save(scenario));
    }

    private Scenario findScenario(UUID scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario", scenarioId));
    }

    private ScenarioSummary summary(Scenario scenario) {
        return ScenarioSummary.from(scenario, difficultyProfileService.forScenario(scenario));
    }
}
