package com.umang.notification.dto.response;

import com.umang.notification.model.entity.Notification;
import com.umang.notification.model.enums.Channel;
import com.umang.notification.model.enums.NotificationStatus;
import java.time.Instant;

/**
 * Read-model of a persisted {@link Notification} for the status/history endpoints.
 */
public record NotificationView(
        Long id,
        String userId,
        String eventKey,
        Channel channel,
        String templateKey,
        NotificationStatus status,
        String failureReason,
        int attempts,
        Instant sendAt,
        Instant sentAt,
        Instant createdAt) {

    public static NotificationView from(Notification n) {
        return new NotificationView(
                n.getId(),
                n.getUserId(),
                n.getEventKey(),
                n.getChannel(),
                n.getTemplateKey(),
                n.getStatus(),
                n.getFailureReason(),
                n.getAttempts(),
                n.getSendAt(),
                n.getSentAt(),
                n.getCreatedAt());
    }
}
