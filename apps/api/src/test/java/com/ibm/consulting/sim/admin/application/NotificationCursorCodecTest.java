package com.ibm.consulting.sim.admin.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationCursorCodecTest {

    private final NotificationCursorCodec codec = new NotificationCursorCodec();

    @Test
    void roundTripsOpaqueCursor() {
        NotificationCursor cursor = new NotificationCursor(
                Instant.parse("2026-09-02T01:02:03Z"), UUID.randomUUID());
        assertEquals(cursor, codec.decode(codec.encode(cursor)));
    }

    @Test
    void rejectsMalformedCursor() {
        assertThrows(InvalidNotificationQueryException.class, () -> codec.decode("not-base64!!!"));
    }
}

