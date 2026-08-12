package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.lead.domain.LeadCatalogPage;

import java.util.List;

/** Stable response contract for the paged lead catalogue; the older scenario lead list stays unchanged. */
public record LeadCatalogResponse(List<LeadSummary> items, long totalElements, int page, int size, int totalPages) {
    public static LeadCatalogResponse from(LeadCatalogPage page) {
        return new LeadCatalogResponse(page.items().stream().map(LeadSummary::from).toList(),
                page.totalElements(), page.page(), page.size(), page.totalPages());
    }
}
