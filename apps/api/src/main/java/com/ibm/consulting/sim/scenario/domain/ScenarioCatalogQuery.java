package com.ibm.consulting.sim.scenario.domain;

import java.util.Locale;

/** Bounded query contract for the learner-facing scenario catalogue. */
public record ScenarioCatalogQuery(String search, String industry, Integer difficulty, int page, int size) {
    private static final int MAX_PAGE_SIZE = 24;

    public ScenarioCatalogQuery {
        search = normalise(search);
        industry = normalise(industry);
        difficulty = difficulty == null ? null : Math.max(1, Math.min(5, difficulty));
        page = Math.max(0, page);
        size = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    public String cacheKey() {
        return "%s|%s|%s|%d|%d".formatted(search, industry, difficulty, page, size);
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
