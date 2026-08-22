package com.ibm.consulting.sim.admin.infrastructure.realtime;

import com.ibm.consulting.sim.identity.domain.UserRole;

import java.util.Locale;

public final class NotificationWebSocketDestinations {

    private static final String CONNECTION_PREFIX = "/ws/notifications/";
    private static final String TOPIC_PREFIX = "/topic/notifications/";

    private NotificationWebSocketDestinations() {}

    public static String connectionEndpoint(UserRole role) {
        return CONNECTION_PREFIX + roleSlug(role);
    }

    public static String subscriptionTopic(UserRole role) {
        return TOPIC_PREFIX + roleSlug(role);
    }

    public static boolean isNotificationTopic(String destination) {
        return destination != null && destination.startsWith(TOPIC_PREFIX);
    }

    private static String roleSlug(UserRole role) {
        return role.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
