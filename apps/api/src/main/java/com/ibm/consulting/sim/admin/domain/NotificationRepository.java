package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.identity.domain.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    void save(NotificationEvent notification);

    List<NotificationEvent> findNotificationsByRole(UserRole role);

    Optional<NotificationEvent> findByEventIdAndRole(UUID eventId , UserRole role);

    Optional<NotificationEvent> findByEventId(UUID eventId);

    void saveAndFlush(NotificationEvent notification);

    boolean existsByEventId(UUID eventId);
}
