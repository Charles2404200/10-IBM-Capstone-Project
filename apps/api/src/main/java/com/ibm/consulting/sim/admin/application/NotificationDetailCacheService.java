package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationCentreRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.ibm.consulting.sim.shared.config.CacheConfig.NOTIFICATION_DETAIL_CACHE;

@Service
public class NotificationDetailCacheService {

    private final NotificationCentreRepository repository;

    public NotificationDetailCacheService(NotificationCentreRepository repository) {
        this.repository = repository;
    }

    @Cacheable(
            cacheNames = NOTIFICATION_DETAIL_CACHE,
            key = "#role.name() + ':' + #eventId",
            unless = "#result == null")
    public NotificationSharedDetail get(UUID eventId, UserRole role) {
        return repository.findDetail(eventId, role)
                .orElseThrow(() -> new NotFoundException("Notification", eventId));
    }
}

