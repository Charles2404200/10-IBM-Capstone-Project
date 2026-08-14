package com.ibm.consulting.sim.lead.domain;

import java.util.List;

/** A read-model page, intentionally independent from Spring Data's HTTP shape. */
public record LeadCatalogPage(List<Lead> items, long totalElements, int page, int size, int totalPages) {
    public LeadCatalogPage {
        items = List.copyOf(items);
    }
}
