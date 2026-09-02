package com.ibm.consulting.sim.admin.application;

import java.util.List;
import java.util.Map;

public record NotificationPageResponse(
        List<Map<String, Object>> items,
        String nextCursor,
        boolean hasMore) {
}

