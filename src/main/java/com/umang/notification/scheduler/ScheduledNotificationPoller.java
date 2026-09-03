package com.umang.notification.scheduler;

import com.umang.notification.config.KafkaConfig;
import com.umang.notification.event.NotificationRequestedEvent;
import com.umang.notification.model.entity.Notification;
import com.umang.notification.model.enums.NotificationStatus;
import com.umang.notification.repository.NotificationRepository;
import com.umang.notification.service.ScheduledNotificationService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes due {@code SCHEDULED} notifications onto the Kafka backbone. Runs on a fixed
 * delay; each tick claims rows whose {@code sendAt <= now}, flips them to {@code QUEUED},
 * and publishes the reconstructed event. From there the normal consumer pipeline (render,
 * preferences, rate limit, send) takes over.
 *
 * <p>Flipping to QUEUED in the same transaction that fetched the row is a lightweight guard
 * against a second app instance re-publishing the same schedule. At real scale this becomes
 * a {@code SELECT ... FOR UPDATE SKIP LOCKED} claim so multiple pollers share the work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledNotificationPoller {

    private static final int BATCH = 100;

    private final NotificationRepository notificationRepository;
    private final ScheduledNotificationService scheduledNotificationService;
    private final KafkaTemplate<String, NotificationRequestedEvent> kafkaTemplate;

    @Scheduled(fixedDelayString = "${scheduler.poll.fixed-delay-ms:5000}")
    @Transactional
    public void publishDue() {
        List<Notification> due = notificationRepository.findByStatusAndSendAtLessThanEqual(
                NotificationStatus.SCHEDULED, Instant.now(), Limit.of(BATCH));
        if (due.isEmpty()) {
            return;
        }
        log.info("Promoting {} scheduled notification(s) onto Kafka", due.size());
        for (Notification n : due) {
            NotificationRequestedEvent event = scheduledNotificationService.toEvent(n);
            n.setStatus(NotificationStatus.QUEUED);
            notificationRepository.save(n);
            kafkaTemplate.send(KafkaConfig.NOTIFICATION_REQUESTS_TOPIC, n.getUserId(), event);
        }
    }
}
