package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationPriorityTest {

    @Test
    void mapsOnlyControlledDomainPrioritiesToInfrastructurePriorities() {
        assertEquals(EventPriority.NORMAL, NotificationPriority.NORMAL.toEventPriority());
        assertEquals(EventPriority.HIGH, NotificationPriority.IMPORTANT.toEventPriority());
        assertEquals(EventPriority.CRITICAL, NotificationPriority.CRITICAL.toEventPriority());
    }
}
