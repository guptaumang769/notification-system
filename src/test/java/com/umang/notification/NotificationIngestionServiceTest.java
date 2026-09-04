package com.umang.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umang.notification.config.KafkaConfig;
import com.umang.notification.dto.request.NotificationRequest;
import com.umang.notification.dto.response.NotificationAccepted;
import com.umang.notification.event.NotificationRequestedEvent;
import com.umang.notification.model.enums.Channel;
import com.umang.notification.service.IdempotencyService;
import com.umang.notification.service.NotificationIngestionService;
import com.umang.notification.service.ScheduledNotificationService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/** Pub/sub ingestion: fan-out, edge-dedupe short-circuit, and the scheduling branch. */
@ExtendWith(MockitoExtension.class)
class NotificationIngestionServiceTest {

    @Mock private KafkaTemplate<String, NotificationRequestedEvent> kafkaTemplate;
    @Mock private IdempotencyService idempotencyService;
    @Mock private ScheduledNotificationService scheduledNotificationService;

    @Test
    void fansOutOneEventPerChannelAndPublishesNow() {
        NotificationIngestionService svc = new NotificationIngestionService(
                kafkaTemplate, idempotencyService, scheduledNotificationService);
        when(idempotencyService.alreadySeen(anyString())).thenReturn(false);

        NotificationRequest req = new NotificationRequest(
                "user-1", "welcome", List.of(Channel.EMAIL, Channel.SMS),
                "welcome", Map.of("name", "Umang"), "idem-1", null);

        NotificationAccepted accepted = svc.publish(req);

        assertThat(accepted.accepted()).isTrue();
        // One publish per requested channel, all keyed by userId.
        verify(kafkaTemplate, times(2))
                .send(eq(KafkaConfig.NOTIFICATION_REQUESTS_TOPIC), eq("user-1"), any());
        verify(scheduledNotificationService, never()).schedule(any());
    }

    @Test
    void alreadySeenKeyIsNotRepublished() {
        NotificationIngestionService svc = new NotificationIngestionService(
                kafkaTemplate, idempotencyService, scheduledNotificationService);
        when(idempotencyService.alreadySeen("idem-1:EMAIL")).thenReturn(true);

        NotificationRequest req = new NotificationRequest(
                "user-1", "welcome", List.of(Channel.EMAIL),
                "welcome", Map.of(), "idem-1", null);

        NotificationAccepted accepted = svc.publish(req);

        assertThat(accepted.accepted()).isFalse();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void futureSendAtIsScheduledNotPublished() {
        NotificationIngestionService svc = new NotificationIngestionService(
                kafkaTemplate, idempotencyService, scheduledNotificationService);
        when(idempotencyService.alreadySeen(anyString())).thenReturn(false);

        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        NotificationRequest req = new NotificationRequest(
                "user-1", "welcome", List.of(Channel.EMAIL),
                "welcome", Map.of(), "idem-1", future);

        svc.publish(req);

        verify(scheduledNotificationService, times(1)).schedule(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
