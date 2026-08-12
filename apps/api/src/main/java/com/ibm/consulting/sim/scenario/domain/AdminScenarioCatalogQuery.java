package com.ibm.consulting.sim.scenario.domain;

import java.util.Locale;

/**
 * Bounded authoring catalogue query. Unlike the learner catalogue, authors can
 * browse draft, active and archived scenario revisions.
 */
public record AdminScenarioCatalogQuery(String search, ScenarioStatus status, int page, int size) {
    private static final int MAX_PAGE_SIZE = 48;

    public AdminScenarioCatalogQuery {
        search = normalise(search);
        page = Math.max(0, page);
        size = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    public String cacheKey() {
        return "%s|%s|%d|%d".formatted(search, status, page, size);
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
