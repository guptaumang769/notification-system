package com.umang.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.notification.event.NotificationRequestedEvent;
import com.umang.notification.model.entity.Notification;
import com.umang.notification.model.enums.NotificationStatus;
import com.umang.notification.repository.NotificationRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles the persistence side of delayed delivery. A request carrying a future
 * {@code sendAt} is NOT published to Kafka immediately; instead a {@code SCHEDULED} row is
 * persisted here (params kept as JSON so it can be rendered later), and the
 * {@code ScheduledNotificationPoller} promotes it onto Kafka once it comes due.
 *
 * <p>Delayed-delivery approaches, for the record: (a) this DB-poller (simple, survives
 * restarts, easy to cancel/reschedule — chosen here); (b) Kafka + a per-message delay via a
 * tiered/delay topic; (c) a scheduler like Quartz; (d) cloud-native (SQS delay queues up to
 * 15 min, or EventBridge Scheduler for arbitrary future times). The poller trades a little
 * latency (poll interval) for operational simplicity and durability.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledNotificationService {

    private static final TypeReference<Map<String, String>> PARAMS_TYPE = new TypeReference<>() {
    };

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    /** Persist a future send as a SCHEDULED row keyed on its idempotency key. */
    @SneakyThrows
    public Notification schedule(NotificationRequestedEvent event) {
        String paramsJson = objectMapper.writeValueAsString(event.params());
        Notification n = notificationRepository
                .findByIdempotencyKey(event.idempotencyKey())
                .orElseGet(() -> Notification.builder()
                        .userId(event.userId())
                        .eventKey(event.eventKey())
                        .channel(event.channel())
                        .templateKey(event.templateKey())
                        .idempotencyKey(event.idempotencyKey())
                        .sendAt(event.sendAt())
                        .attempts(0)
                        .status(NotificationStatus.SCHEDULED)
                        .build());
        n.setParamsJson(paramsJson);
        log.info("Scheduled {} for user {} at {}", event.channel(), event.userId(), event.sendAt());
        return notificationRepository.save(n);
    }

    /** Reconstruct the event from a stored SCHEDULED row so the poller can publish it. */
    @SneakyThrows
    public NotificationRequestedEvent toEvent(Notification n) {
        Map<String, String> params = n.getParamsJson() == null
                ? Map.of()
                : objectMapper.readValue(n.getParamsJson(), PARAMS_TYPE);
        return new NotificationRequestedEvent(
                n.getIdempotencyKey(),
                n.getUserId(),
                n.getEventKey(),
                n.getChannel(),
                n.getTemplateKey(),
                params,
                n.getSendAt(),
                n.getCreatedAt());
    }
}
