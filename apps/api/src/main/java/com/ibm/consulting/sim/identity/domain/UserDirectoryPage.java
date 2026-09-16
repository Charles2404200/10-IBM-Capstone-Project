package com.ibm.consulting.sim.identity.domain;

import java.util.List;

/** Page of user entities returned from the persistence boundary. */
public record UserDirectoryPage(List<User> items, long totalElements, int page, int size, int totalPages) {
    public UserDirectoryPage {
        items = List.copyOf(items);
    }
}
