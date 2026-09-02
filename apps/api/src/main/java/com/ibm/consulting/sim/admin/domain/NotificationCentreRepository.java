package com.ibm.consulting.sim.admin.domain;

import com.ibm.consulting.sim.admin.application.NotificationCursor;
import com.ibm.consulting.sim.admin.application.NotificationQueryField;
import com.ibm.consulting.sim.admin.application.NotificationSharedDetail;
import com.ibm.consulting.sim.admin.application.ProjectedNotification;
import com.ibm.consulting.sim.identity.domain.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface NotificationCentreRepository {

    List<ProjectedNotification> findPage(
            UUID userId,
            UserRole role,
            NotificationCursor cursor,
            int limit,
            Set<NotificationQueryField> fields);

    Optional<NotificationSharedDetail> findDetail(UUID eventId, UserRole role);

    Optional<Instant> findReadAt(UUID eventId, UUID userId, UserRole role);

    long countUnread(UUID userId, UserRole role);
}

