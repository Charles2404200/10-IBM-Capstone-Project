package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.admin.domain.*;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

@Repository
interface SpringDataNotificationReadRepository extends JpaRepository<NotificationRead, UUID> {
    List<NotificationRead> findByNotificationId(UUID notificationId);
    List<NotificationRead> findByUserIdAndNotificationIdIn(
            UUID userId,
            Collection<UUID> notificationIds
    );
    // 2 threads can insert in parallel
    // at the same time and it's hard to track
    // in the application level so we need to write
    // for database level stuff since at that level
    // it will through error if the constraint is violated
    @Modifying
    @Query(value = """
            INSERT INTO notification_reads
                (id, notification_id, user_id, read_at, created_at, updated_at, version)
            VALUES
                (:id, :notificationId, :userId, :readAt, :readAt, :readAt, 0)
            ON CONFLICT (notification_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("notificationId") UUID notificationId,
                       @Param("userId") UUID userId,
                       @Param("readAt") Instant readAt);
}

@Repository
public class JpaNotificationReadRepository implements NotificationReadRepository {

    private final SpringDataNotificationReadRepository readRepository;

    public JpaNotificationReadRepository(SpringDataNotificationReadRepository readRepository)
    {
        this.readRepository = readRepository;
    }

    /**
     * @param userId
     * @return
     */
    @Override
    public List<NotificationRead> findReadNotificationsByUserId(
            UUID userId,
            Collection<UUID> notificationIds) {
        if (notificationIds.isEmpty()) {
            return List.of();
        }
        return readRepository.findByUserIdAndNotificationIdIn(userId, notificationIds);
    }

    @Override
    public boolean createReadNotificationForUser(
            UUID notificationId,
            UUID userId,
            UserRole role) {

        Instant readAt = Instant.now();

        int inserted = readRepository.insertIfAbsent(
                UUID.randomUUID(),
                notificationId,
                userId,
                readAt
        );

        return inserted == 1;
    }

    public List<NotificationRead> findByNotificationId(UUID notificationId)
    {
        return readRepository.findByNotificationId(notificationId);
    }

}
