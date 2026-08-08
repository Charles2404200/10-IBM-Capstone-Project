package com.ibm.consulting.sim.scenario.api;

import com.ibm.consulting.sim.scenario.application.CreatePersonaRequest;
import com.ibm.consulting.sim.scenario.application.CreateScenarioRequest;
import com.ibm.consulting.sim.scenario.application.ScenarioService;
import com.ibm.consulting.sim.scenario.application.ScenarioSummary;
import com.ibm.consulting.sim.scenario.application.UpdateRubricWeightsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Scenario/persona content authoring API. Restricted to {@code SCENARIO_AUTHOR}
 * and {@code ADMINISTRATOR} — the learner-facing {@link ScenarioController} stays
 * strictly read-only, keeping content authoring and content consumption cleanly
 * separated (command/query separation at the API boundary).
 */
@RestController
@RequestMapping("/api/v1/admin/scenarios")
@PreAuthorize("hasAnyRole('SCENARIO_AUTHOR', 'ADMINISTRATOR')")
public class AdminScenarioController {

    private final ScenarioService scenarioService;

    public AdminScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    List<ScenarioSummary> listAll() {
        return scenarioService.listAllForAdmin();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ScenarioSummary create(@Valid @RequestBody CreateScenarioRequest request) {
        return scenarioService.create(request);
    }

    @PostMapping("/{scenarioId}/personas")
    @ResponseStatus(HttpStatus.CREATED)
    ScenarioSummary addPersona(@PathVariable UUID scenarioId, @Valid @RequestBody CreatePersonaRequest request) {
        return scenarioService.addPersona(scenarioId, request);
    }

    @PatchMapping("/{scenarioId}/publish")
    ScenarioSummary publish(@PathVariable UUID scenarioId) {
        return scenarioService.publish(scenarioId);
    }

    @PatchMapping("/{scenarioId}/archive")
    ScenarioSummary archive(@PathVariable UUID scenarioId) {
        return scenarioService.archive(scenarioId);
    }

    @PutMapping("/{scenarioId}/rubric")
    ScenarioSummary updateRubric(@PathVariable UUID scenarioId,
                                 @Valid @RequestBody UpdateRubricWeightsRequest request) {
        return scenarioService.updateRubricWeights(scenarioId, request.weights());
    }
}
