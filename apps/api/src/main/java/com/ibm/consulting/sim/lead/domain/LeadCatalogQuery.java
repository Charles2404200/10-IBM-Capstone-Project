package com.ibm.consulting.sim.lead.domain;

import java.util.Locale;
import java.util.UUID;

/** Immutable, bounded query contract for the learner-facing lead catalogue. */
public record LeadCatalogQuery(UUID scenarioId, String search, String industry, LeadDifficulty difficulty,
                               int page, int size) {
    private static final int MAX_PAGE_SIZE = 48;

    public LeadCatalogQuery {
        search = normalise(search);
        industry = normalise(industry);
        page = Math.max(0, page);
        size = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    public String cacheKey() {
        return "%s|%s|%s|%s|%d|%d".formatted(scenarioId, search, industry, difficulty, page, size);
    }

    private static String normalise(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
