package com.ibm.consulting.sim.scenario.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Central resolver for persisted scenario profiles and immutable engagement snapshots. */
@Service
public class DifficultyProfileService {
    private final ObjectMapper objectMapper;
    private final ScenarioRepository scenarioRepository;

    public DifficultyProfileService(ObjectMapper objectMapper, ScenarioRepository scenarioRepository) {
        this.objectMapper = objectMapper;
        this.scenarioRepository = scenarioRepository;
    }

    public DifficultyProfile forScenario(Scenario scenario) {
        return decodeOrDefault(scenario.getDifficultyProfileConfig(), scenario);
    }

    public DifficultyProfile forEngagement(Engagement engagement) {
        String snapshot = engagement.getDifficultyProfileSnapshot();
        if (snapshot != null && !snapshot.isBlank()) return decode(snapshot);
        Scenario scenario = scenarioRepository.findById(engagement.getScenarioId())
                .orElseThrow(() -> new NotFoundException("Scenario", engagement.getScenarioId()));
        return forScenario(scenario);
    }

    public String snapshot(DifficultyProfile profile) { return encode(profile); }

    public void updateScenarioProfile(Scenario scenario, DifficultyProfile profile) {
        scenario.updateDifficultyProfileConfig(encode(profile));
    }

    private DifficultyProfile decodeOrDefault(String config, Scenario scenario) {
        if (config == null || config.isBlank()) {
            return DifficultyProfile.defaults(scenario.getDifficulty(), scenario.getInformationAmbiguity(),
                    scenario.getStakeholderComplexity(), scenario.getCommercialPressure());
        }
        return decode(config);
    }

    private DifficultyProfile decode(String value) {
        try { return objectMapper.readValue(value, DifficultyProfile.class); }
        catch (JsonProcessingException exception) { throw new InvalidDifficultyProfileException(exception); }
    }

    private String encode(DifficultyProfile profile) {
        try { return objectMapper.writeValueAsString(profile); }
        catch (JsonProcessingException exception) { throw new InvalidDifficultyProfileException(exception); }
    }

    public static class InvalidDifficultyProfileException extends DomainException {
        InvalidDifficultyProfileException(Exception cause) { super("Difficulty profile is not valid", cause); }
    }
}
