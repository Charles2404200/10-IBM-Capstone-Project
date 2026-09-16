package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.UserDirectoryPage;

import java.util.List;

/** API-safe page of user summaries for the administrative directory. */
public record AdminUserPage(List<UserSummary> items, long totalElements, int page, int size, int totalPages) {
    public static AdminUserPage from(UserDirectoryPage page) {
        return new AdminUserPage(
                page.items().stream().map(UserSummary::from).toList(),
                page.totalElements(), page.page(), page.size(), page.totalPages());
    }
}
