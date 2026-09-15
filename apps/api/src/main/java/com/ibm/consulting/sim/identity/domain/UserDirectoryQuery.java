package com.ibm.consulting.sim.identity.domain;

import java.util.Locale;

/** Bounded, filterable administrative directory query. */
public record UserDirectoryQuery(String search, UserRole role, Boolean active, int page, int size) {
    private static final int MAX_PAGE_SIZE = 100;

    public UserDirectoryQuery {
        search = normaliseSearch(search);
        page = Math.max(0, page);
        size = Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    public String cacheKey() {
        return String.join("|",
                search == null ? "" : search,
                role == null ? "" : role.name(),
                active == null ? "" : active.toString(),
                Integer.toString(page),
                Integer.toString(size));
    }

    private static String normaliseSearch(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
