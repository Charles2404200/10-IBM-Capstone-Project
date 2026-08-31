package com.ibm.consulting.sim.admin.domain;


import com.ibm.consulting.sim.identity.domain.UserRole;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface NotificationReadRepository {

    List<NotificationRead> findReadNotificationsByUserId(
            UUID userId,
            Collection<UUID> notificationIds
    );

    boolean createReadNotificationForUser(
            UUID eventId,
            UUID userId,
            UserRole role
    );

    List<NotificationRead> findByNotificationId(UUID notificationId);

}
