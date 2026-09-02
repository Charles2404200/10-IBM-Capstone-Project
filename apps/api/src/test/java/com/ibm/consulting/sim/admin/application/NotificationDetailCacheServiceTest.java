package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationCentreRepository;
import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {
        CacheConfig.class,
        NotificationDetailCacheService.class,
        NotificationDetailCacheServiceTest.Dependencies.class
})
class NotificationDetailCacheServiceTest {

    @Autowired
    private NotificationDetailCacheService service;

    @Autowired
    private NotificationCentreRepository repository;

    @Test
    void immutableRoleScopedDetailUsesTargetedCacheKey() {
        UUID eventId = UUID.randomUUID();
        NotificationSharedDetail detail = new NotificationSharedDetail(
                eventId, "Course", "Full body", NotificationPriority.IMPORTANT, Instant.now());
        when(repository.findDetail(eventId, UserRole.LEARNER)).thenReturn(Optional.of(detail));

        assertEquals(detail, service.get(eventId, UserRole.LEARNER));
        assertEquals(detail, service.get(eventId, UserRole.LEARNER));

        // this verifies checks whether the method is called with exactly
        // those arguments
        verify(repository).findDetail(eventId, UserRole.LEARNER);
    }

    @TestConfiguration
    static class Dependencies {
        @Bean
        NotificationCentreRepository notificationCentreRepository() {
            return mock(NotificationCentreRepository.class);
        }
    }
}

