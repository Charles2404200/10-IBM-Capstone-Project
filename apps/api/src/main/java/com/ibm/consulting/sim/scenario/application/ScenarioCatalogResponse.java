package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.ScenarioCatalogPage;

import java.util.List;

/** Stable, paged response used by Command Centre without loading the whole catalogue. */
public record ScenarioCatalogResponse(List<ScenarioSummary> items, long totalElements, int page, int size,
                                      int totalPages) {
    public static ScenarioCatalogResponse from(ScenarioCatalogPage page,
                                               java.util.function.Function<com.ibm.consulting.sim.scenario.domain.Scenario, ScenarioSummary> mapper) {
        return new ScenarioCatalogResponse(page.items().stream().map(mapper).toList(), page.totalElements(),
                page.page(), page.size(), page.totalPages());
    }
}
