package com.ibm.consulting.sim.scenario.domain;

import java.util.List;

/** Database page detached from Spring Data's transport types. */
public record ScenarioCatalogPage(List<Scenario> items, long totalElements, int page, int size, int totalPages) {
    public ScenarioCatalogPage {
        items = List.copyOf(items);
    }
}
