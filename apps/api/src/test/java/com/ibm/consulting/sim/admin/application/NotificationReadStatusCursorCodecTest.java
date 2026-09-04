package com.ibm.consulting.sim.admin.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationReadStatusCursorCodecTest {

    private final NotificationReadStatusCursorCodec codec =
            new NotificationReadStatusCursorCodec();

    @Test
    void roundTripsUnicodeDisplayNameAndStableUserTieBreaker() {
        UUID userId = UUID.randomUUID();

        NotificationReadStatusCursor decoded = codec.decode(codec.encode(
                new NotificationReadStatusCursor("Zoë.Example", userId)));

        assertEquals("zoë.example", decoded.displayName());
        assertEquals(userId, decoded.userId());
    }

    @Test
    void blankCursorMeansFirstPage() {
        assertNull(codec.decode(" "));
    }

    @Test
    void malformedOrOversizedCursorIsRejected() {
        assertThrows(InvalidNotificationQueryException.class, () -> codec.decode("invalid"));
        assertThrows(InvalidNotificationQueryException.class, () -> codec.decode("x".repeat(257)));
    }
}
