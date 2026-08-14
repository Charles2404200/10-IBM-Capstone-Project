package com.ibm.consulting.sim.scenario.api;

import com.ibm.consulting.sim.scenario.application.CreatePersonaRequest;
import com.ibm.consulting.sim.scenario.application.CreateScenarioRequest;
import com.ibm.consulting.sim.scenario.application.ScenarioService;
import com.ibm.consulting.sim.scenario.application.ScenarioSummary;
import com.ibm.consulting.sim.scenario.application.UpdateRubricWeightsRequest;
import com.ibm.consulting.sim.scenario.application.UpdateDifficultyProfileRequest;
import com.ibm.consulting.sim.scenario.application.UpdateScenarioBlueprintRequest;
import com.ibm.consulting.sim.scenario.application.UpdateScenarioAuthoringConfigRequest;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringView;
import com.ibm.consulting.sim.scenario.application.ScenarioCatalogResponse;
import com.ibm.consulting.sim.scenario.application.LeadAuthoringRequest;
import com.ibm.consulting.sim.scenario.application.LeadAuthoringView;
import com.ibm.consulting.sim.scenario.domain.AdminScenarioCatalogQuery;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import com.ibm.consulting.sim.lead.application.LeadSummary;
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

    /** Fast, bounded library query for the authoring console. The legacy list endpoint remains available. */
    @GetMapping("/catalog")
    ScenarioCatalogResponse listCatalog(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) ScenarioStatus status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "12") int size) {
        return scenarioService.listCatalogForAdmin(new AdminScenarioCatalogQuery(search, status, page, size));
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

    @GetMapping("/{scenarioId}/authoring")
    ScenarioAuthoringView authoringView(@PathVariable UUID scenarioId) {
        return scenarioService.authoringView(scenarioId);
    }

    @PutMapping("/{scenarioId}/blueprint")
    ScenarioAuthoringView updateBlueprint(@PathVariable UUID scenarioId,
                                          @Valid @RequestBody UpdateScenarioBlueprintRequest request) {
        return scenarioService.updateBlueprint(scenarioId, request);
    }

    @PutMapping("/{scenarioId}/authoring-config")
    ScenarioAuthoringView updateAuthoringConfig(@PathVariable UUID scenarioId,
                                                @Valid @RequestBody UpdateScenarioAuthoringConfigRequest request) {
        return scenarioService.updateAuthoringConfig(scenarioId, request.config());
    }

    @PostMapping("/{scenarioId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    ScenarioAuthoringView createRevision(@PathVariable UUID scenarioId) {
        return scenarioService.createRevision(scenarioId);
    }

    @PostMapping("/{scenarioId}/leads")
    @ResponseStatus(HttpStatus.CREATED)
    LeadSummary createLead(@PathVariable UUID scenarioId, @Valid @RequestBody LeadAuthoringRequest request) {
        return scenarioService.createLead(scenarioId, request);
    }

    @GetMapping("/{scenarioId}/leads")
    List<LeadAuthoringView> listAuthoringLeads(@PathVariable UUID scenarioId) {
        return scenarioService.listAuthoringLeads(scenarioId);
    }

    @PutMapping("/{scenarioId}/leads/{leadId}")
    LeadSummary updateLead(@PathVariable UUID scenarioId, @PathVariable UUID leadId,
                           @Valid @RequestBody LeadAuthoringRequest request) {
        return scenarioService.updateLead(scenarioId, leadId, request);
    }

    @DeleteMapping("/{scenarioId}/leads/{leadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLead(@PathVariable UUID scenarioId, @PathVariable UUID leadId) {
        scenarioService.deleteLead(scenarioId, leadId);
    }

    @PutMapping("/{scenarioId}/difficulty-profile")
    ScenarioSummary updateDifficultyProfile(@PathVariable UUID scenarioId,
                                            @Valid @RequestBody UpdateDifficultyProfileRequest request) {
        return scenarioService.updateDifficultyProfile(scenarioId, request);
    }
}
