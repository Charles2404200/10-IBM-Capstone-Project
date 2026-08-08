package com.ibm.consulting.sim.scenario.api;

import com.ibm.consulting.sim.scenario.application.ScenarioService;
import com.ibm.consulting.sim.scenario.application.ScenarioSummary;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    List<ScenarioSummary> listActive() {
        return scenarioService.listActive();
    }

    @GetMapping("/{id}")
    ScenarioSummary getById(@PathVariable UUID id) {
        return scenarioService.getById(id);
    }
}
