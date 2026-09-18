package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.identity.domain.UserRole;

import java.util.List;

/** Application result returned after Kafka acknowledges every role notification. */
public record NotificationPublishResult(
        int publishedCount,
        List<UserRole> roles) {

    public NotificationPublishResult {
        roles = List.copyOf(roles);
    }
}
