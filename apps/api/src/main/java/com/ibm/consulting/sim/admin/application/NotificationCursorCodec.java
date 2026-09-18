package com.ibm.consulting.sim.admin.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class NotificationCursorCodec {

    public String encode(NotificationCursor cursor) {
        String value = cursor.createdAt() + "|" + cursor.eventId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public NotificationCursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 256) {
            throw invalidCursor();
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            return new NotificationCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private InvalidNotificationQueryException invalidCursor() {
        return new InvalidNotificationQueryException("Invalid notification cursor");
    }
}
