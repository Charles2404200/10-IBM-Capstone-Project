package com.ibm.consulting.sim.scenario.api;

import com.ibm.consulting.sim.scenario.application.ScenarioService;
import com.ibm.consulting.sim.scenario.application.ScenarioCatalogResponse;
import com.ibm.consulting.sim.scenario.application.ScenarioSummary;
import com.ibm.consulting.sim.scenario.domain.ScenarioCatalogQuery;
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

    /** Paged learner catalogue. The original collection endpoint stays available for existing clients. */
    @GetMapping("/catalog")
    ScenarioCatalogResponse listCatalog(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        return scenarioService.listCatalog(new ScenarioCatalogQuery(search, industry, difficulty, page, size));
    }

    @GetMapping("/catalog/industries")
    List<String> listCatalogIndustries() {
        return scenarioService.listCatalogIndustries();
    }

    @GetMapping("/{id}")
    ScenarioSummary getById(@PathVariable UUID id) {
        return scenarioService.getById(id);
    }
}
