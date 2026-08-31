package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.admin.domain.NotificationEvent;
import com.ibm.consulting.sim.admin.domain.NotificationRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataNotificationRepository extends JpaRepository<NotificationEvent, UUID> {
    List<NotificationEvent> findByRoleOrderByCreatedAtDesc(UserRole role, Pageable pageable);
    Optional<NotificationEvent> findByEventId(UUID eventId);
    Optional<NotificationEvent> findByEventIdAndRole(UUID eventId, UserRole role);
    boolean existsByEventId(UUID eventId);
}

@Repository
class JpaNotificationRepository implements NotificationRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaNotificationRepository.class);

    private final SpringDataNotificationRepository notificationRepository;

    JpaNotificationRepository(SpringDataNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        return notificationRepository.existsByEventId(eventId);
    }

    @Override
    public void save(NotificationEvent notification) {
        NotificationEvent saved = notificationRepository.save(notification);
    }

    @Override
    public void saveAndFlush(NotificationEvent notification) {
        notificationRepository.saveAndFlush(notification);
    }

    @Override
    public Optional<NotificationEvent> findByEventIdAndRole(UUID eventId, UserRole role)
    {
        return notificationRepository.findByEventIdAndRole(eventId,role);
    }

    @Override
    public List<NotificationEvent> findNotificationsByRole(UserRole role, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return notificationRepository.findByRoleOrderByCreatedAtDesc(
                role,
                PageRequest.of(0, limit)
        );
    }


    @Override
    public Optional<NotificationEvent> findByEventId(UUID eventId) {
        return notificationRepository.findByEventId(eventId);
    }


}
