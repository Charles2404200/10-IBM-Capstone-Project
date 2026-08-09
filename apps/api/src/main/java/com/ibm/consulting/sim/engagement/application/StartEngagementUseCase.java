package com.ibm.consulting.sim.engagement.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StartEngagementUseCase {

    private final EngagementRepository engagementRepository;
    private final ScenarioRepository scenarioRepository;
    private final DifficultyProfileService difficultyProfileService;

    public StartEngagementUseCase(EngagementRepository engagementRepository,
                                  ScenarioRepository scenarioRepository,
                                  DifficultyProfileService difficultyProfileService) {
        this.engagementRepository = engagementRepository;
        this.scenarioRepository = scenarioRepository;
        this.difficultyProfileService = difficultyProfileService;
    }

    /** Starts an engagement using the first persona defined for the scenario. */
    @Transactional
    public EngagementResponse execute(UUID userId, UUID scenarioId) {
        return execute(userId, scenarioId, null);
    }

    /**
     * Starts an engagement against a specific stakeholder persona. When {@code personaId}
     * is {@code null}, the first persona defined for the scenario is used, preserving
     * backward-compatible behaviour for single-persona scenarios.
     */
    @Transactional
    public EngagementResponse execute(UUID userId, UUID scenarioId, UUID personaId) {
        var scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario", scenarioId));

        Persona persona = personaId == null
                ? scenario.getPersonas().stream().findFirst()
                        .orElseThrow(() -> new NotFoundException("Persona for scenario", scenarioId))
                : scenario.getPersonas().stream()
                        .filter(p -> p.getId().equals(personaId))
                        .findFirst()
                        .orElseThrow(() -> new PersonaNotInScenarioException(personaId, scenarioId));

        Engagement engagement = Engagement.start(userId, scenarioId, persona.getId(),
                difficultyProfileService.snapshot(difficultyProfileService.forScenario(scenario)));
        engagementRepository.save(engagement);
        return EngagementResponse.from(engagement);
    }

    public static class PersonaNotInScenarioException extends DomainException {
        public PersonaNotInScenarioException(UUID personaId, UUID scenarioId) {
            super("Persona " + personaId + " does not belong to scenario " + scenarioId);
        }
    }
}
