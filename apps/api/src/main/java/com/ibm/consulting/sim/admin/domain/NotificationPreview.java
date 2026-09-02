package com.ibm.consulting.sim.admin.domain;

import java.util.Objects;

/** Creates bounded previews without splitting a Unicode code point. */
public final class NotificationPreview {

    public static final int MAX_CODE_POINTS = 180;

    private NotificationPreview() {
    }

    public static String from(String message) {
        String value = Objects.requireNonNull(message, "message must not be null");
        int count = value.codePointCount(0, value.length());
        if (count <= MAX_CODE_POINTS) {
            return value;
        }
        int end = value.offsetByCodePoints(0, MAX_CODE_POINTS - 1);
        return value.substring(0, end).stripTrailing() + '…';
    }
}

