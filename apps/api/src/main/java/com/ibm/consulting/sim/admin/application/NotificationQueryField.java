package com.ibm.consulting.sim.admin.application;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Public notification-list fields. Values are identifiers, never SQL fragments. */
public enum NotificationQueryField {
    EVENT_ID("eventId"),
    TOPIC_NAME("topicName"),
    MESSAGE_PREVIEW("messagePreview"),
    PRIORITY("priority"),
    CREATED_AT("createdAt"),
    IS_READ("isRead");

    private final String apiName;

    NotificationQueryField(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    public static Set<NotificationQueryField> defaults() {
        return Collections.unmodifiableSet(EnumSet.allOf(NotificationQueryField.class));
    }

    public static Set<NotificationQueryField> parse(String requestedFields) {
        if (requestedFields == null || requestedFields.isBlank()) {
            return defaults();
        }
        if (requestedFields.length() > 256) {
            throw new InvalidNotificationQueryException("fields is too long");
        }

        Set<NotificationQueryField> fields = new LinkedHashSet<>();
        for (String raw : requestedFields.split(",", -1)) {
            String name = raw.trim();
            NotificationQueryField field = Arrays.stream(values())
                    .filter(candidate -> candidate.apiName.equals(name))
                    .findFirst()
                    .orElseThrow(() -> new InvalidNotificationQueryException(
                            "Unsupported notification field: " + name));
            fields.add(field);
        }
        if (fields.isEmpty()) {
            throw new InvalidNotificationQueryException("At least one field is required");
        }
        return Collections.unmodifiableSet(fields);
    }
}

