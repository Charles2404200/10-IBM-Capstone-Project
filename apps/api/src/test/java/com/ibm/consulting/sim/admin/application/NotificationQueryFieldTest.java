package com.ibm.consulting.sim.admin.application;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationQueryFieldTest {

    @Test
    void acceptsOnlyExplicitPublicProjectionFields() {
        assertEquals(
                Set.of(NotificationQueryField.EVENT_ID, NotificationQueryField.TOPIC_NAME),
                NotificationQueryField.parse("eventId,topicName"));
    }

    @Test
    void rejectsFullMessageSecretAndInjectionStyleFields() {
        assertThrows(InvalidNotificationQueryException.class,
                () -> NotificationQueryField.parse("message"));
        assertThrows(InvalidNotificationQueryException.class,
                () -> NotificationQueryField.parse("someSecretField"));
        assertThrows(InvalidNotificationQueryException.class,
                () -> NotificationQueryField.parse("eventId,(SELECT password FROM users)"));
    }
}

