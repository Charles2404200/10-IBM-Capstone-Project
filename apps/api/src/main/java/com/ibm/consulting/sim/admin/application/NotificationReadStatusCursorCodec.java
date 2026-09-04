package com.ibm.consulting.sim.admin.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/** Opaque bounded keyset cursor for the administrator recipient list. */
@Component
public class NotificationReadStatusCursorCodec {

    public String encode(NotificationReadStatusCursor cursor) {
        String name = Base64.getUrlEncoder().withoutPadding().encodeToString(
                cursor.displayName().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return name + "." + cursor.userId();
    }

    public NotificationReadStatusCursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 256) {
            throw invalidCursor();
        }
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank()) {
                throw invalidCursor();
            }
            String name = new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (name.isBlank() || name.length() > 80) {
                throw invalidCursor();
            }
            return new NotificationReadStatusCursor(name, UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private InvalidNotificationQueryException invalidCursor() {
        return new InvalidNotificationQueryException("Invalid notification read-status cursor");
    }
}
