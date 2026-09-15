package com.ibm.consulting.sim.scenario.api;

import com.ibm.consulting.sim.scenario.application.ScenarioLifecycleService;
import com.ibm.consulting.sim.scenario.application.ScenarioLifecycleView;
import com.ibm.consulting.sim.scenario.application.UpdateScenarioLifecycleRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/scenarios/{scenarioId}/lifecycle")
@PreAuthorize("hasAnyRole('SCENARIO_AUTHOR', 'ADMINISTRATOR')")
public class AdminScenarioLifecycleController {
    private final ScenarioLifecycleService service;

    public AdminScenarioLifecycleController(ScenarioLifecycleService service) { this.service = service; }

    @GetMapping
    ScenarioLifecycleView get(@PathVariable UUID scenarioId) { return service.get(scenarioId); }

    @PutMapping
    ScenarioLifecycleView update(@PathVariable UUID scenarioId, @Valid @RequestBody UpdateScenarioLifecycleRequest request) {
        return service.update(scenarioId, request);
    }
}
