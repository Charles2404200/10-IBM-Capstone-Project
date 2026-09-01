package com.ibm.consulting.sim.admin.api;

import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminPlatformControllerPriorityTest {

    @Test
    void omittedPriorityDefaultsToNormal() {
        var request = new AdminPlatformController.PublishNotificationRequest(
                "Update", "Maintenance tonight", List.of(UserRole.LEARNER), null);

        assertEquals(NotificationPriority.NORMAL, request.effectivePriority());
    }

    @Test
    void criticalPriorityIsPreserved() {
        var request = new AdminPlatformController.PublishNotificationRequest(
                "Security", "Reset your password", List.of(UserRole.LEARNER),
                NotificationPriority.CRITICAL);

        assertEquals(NotificationPriority.CRITICAL, request.effectivePriority());
    }

    @Test
    void importantPriorityIsPreserved() {
        var request = new AdminPlatformController.PublishNotificationRequest(
                "New course", "A new course is available", List.of(UserRole.LEARNER),
                NotificationPriority.IMPORTANT);

        assertEquals(NotificationPriority.IMPORTANT, request.effectivePriority());
    }
}
