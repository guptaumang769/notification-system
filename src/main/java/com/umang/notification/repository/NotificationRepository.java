package com.umang.notification.repository;

import com.umang.notification.model.entity.Notification;
import com.umang.notification.model.enums.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Keyset (seek) pagination over a user's history: newest first, fetch rows with id
     * below the last-seen cursor. Keyset beats OFFSET on large tables — it stays O(limit)
     * regardless of how deep the caller pages, because it rides the primary-key index.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId AND n.id < :cursor
            ORDER BY n.id DESC
            """)
    List<Notification> findUserHistory(
            @Param("userId") String userId, @Param("cursor") Long cursor, Limit limit);

    /** Due scheduled notifications the poller should promote onto Kafka. */
    List<Notification> findByStatusAndSendAtLessThanEqual(
            NotificationStatus status, Instant now, Limit limit);
}
