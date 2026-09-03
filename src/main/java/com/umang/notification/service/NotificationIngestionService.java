package com.umang.notification.service;

import com.umang.notification.config.KafkaConfig;
import com.umang.notification.dto.request.NotificationRequest;
import com.umang.notification.dto.response.NotificationAccepted;
import com.umang.notification.event.NotificationRequestedEvent;
import com.umang.notification.model.enums.Channel;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * The event-driven entry point. Validates the request, then fans it out to one
 * {@link NotificationRequestedEvent} per requested channel and publishes each to the
 * {@code notification-requests} Kafka topic. Producers are decoupled from delivery: this
 * method returns as soon as the events are on the log — the consumer does the actual work.
 *
 * <p>Idempotency is enforced end-to-end. Here we per-channel derive a stable key
 * ({@code idempotencyKey:CHANNEL}) and short-circuit at the edge if it's already been seen,
 * so a client retrying the same POST doesn't even re-publish. The consumer re-checks
 * (Redis SETNX + the DB unique constraint) to cover Kafka's at-least-once redelivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationIngestionService {

    private final KafkaTemplate<String, NotificationRequestedEvent> kafkaTemplate;
    private final IdempotencyService idempotencyService;
    private final ScheduledNotificationService scheduledNotificationService;

    public NotificationAccepted publish(NotificationRequest request) {
        Instant now = Instant.now();
        boolean future = request.sendAt() != null && request.sendAt().isAfter(now);
        boolean anyAccepted = false;

        for (Channel channel : request.channels()) {
            String perChannelKey = request.idempotencyKey() + ":" + channel;

            if (idempotencyService.alreadySeen(perChannelKey)) {
                log.info("Skip publish — already seen key {}", perChannelKey);
                continue;
            }

            NotificationRequestedEvent event = new NotificationRequestedEvent(
                    perChannelKey,
                    request.userId(),
                    request.eventKey(),
                    channel,
                    request.templateKey(),
                    request.params(),
                    request.sendAt(),
                    now);

            if (future) {
                // Delayed delivery: persist as SCHEDULED; the poller publishes when due.
                scheduledNotificationService.schedule(event);
            } else {
                // Send-now: publish onto the pub/sub backbone. Key by userId so all of a
                // user's events land on the same partition, preserving per-user ordering
                // under partitioned parallel consumers.
                kafkaTemplate.send(KafkaConfig.NOTIFICATION_REQUESTS_TOPIC, request.userId(), event);
                log.info("Published {} notification for user {} (key {})",
                        channel, request.userId(), perChannelKey);
            }
            anyAccepted = true;
        }

        List<String> channelNames = request.channels().stream().map(Enum::name).toList();
        String message;
        if (!anyAccepted) {
            message = "Duplicate request — nothing accepted";
        } else if (future) {
            message = "Notification scheduled for " + request.sendAt() + " on " + channelNames;
        } else {
            message = "Notification request published to " + channelNames;
        }
        return new NotificationAccepted(request.idempotencyKey(), channelNames, anyAccepted, message);
    }
}
