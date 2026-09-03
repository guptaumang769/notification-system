package com.umang.notification.consumer;

import com.umang.notification.config.KafkaConfig;
import com.umang.notification.event.NotificationRequestedEvent;
import com.umang.notification.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscriber on the {@code notification-requests} topic — the consuming half of the pub/sub
 * backbone. It simply delegates to {@link NotificationDeliveryService}; the container's
 * {@code DefaultErrorHandler} (see {@link KafkaConfig}) owns retry + DLT routing.
 *
 * <p>By letting {@code TransientChannelException} propagate, we hand control to that error
 * handler: it retries the record a few times with backoff, then publishes it to
 * {@code notification-requests.DLT} for out-of-band inspection/replay. Terminal outcomes
 * (sent, skipped, rate-limited, permanent failure) return normally so the offset commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationDeliveryService deliveryService;

    @KafkaListener(
            topics = KafkaConfig.NOTIFICATION_REQUESTS_TOPIC,
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onNotificationRequested(NotificationRequestedEvent event) {
        log.debug("Consumed event for user {} channel {} (key {})",
                event.userId(), event.channel(), event.idempotencyKey());
        deliveryService.deliver(event);
    }
}
