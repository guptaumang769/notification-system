package com.umang.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umang.notification.channel.ChannelFactory;
import com.umang.notification.channel.NotificationChannel;
import com.umang.notification.channel.TransientChannelException;
import com.umang.notification.event.NotificationRequestedEvent;
import com.umang.notification.model.entity.Notification;
import com.umang.notification.model.enums.Channel;
import com.umang.notification.model.enums.NotificationStatus;
import com.umang.notification.repository.NotificationRepository;
import com.umang.notification.service.IdempotencyService;
import com.umang.notification.service.NotificationDeliveryService;
import com.umang.notification.service.PreferenceService;
import com.umang.notification.service.RateLimiterService;
import com.umang.notification.service.TemplateService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The delivery pipeline gates — the effectively-once and policy behaviour that a system-
 * design interviewer would probe. Pure Mockito, no Kafka/Redis/Postgres.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock private IdempotencyService idempotencyService;
    @Mock private PreferenceService preferenceService;
    @Mock private RateLimiterService rateLimiterService;
    @Mock private TemplateService templateService;
    @Mock private ChannelFactory channelFactory;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationChannel sender;

    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new NotificationDeliveryService(
                idempotencyService, preferenceService, rateLimiterService,
                templateService, channelFactory, notificationRepository);
    }

    private NotificationRequestedEvent event() {
        return new NotificationRequestedEvent(
                "idem-1:EMAIL", "user-1", "welcome", Channel.EMAIL, "welcome",
                Map.of("name", "Umang"), null, Instant.now());
    }

    private void happyPathStubs() {
        when(idempotencyService.markIfFirst(anyString())).thenReturn(true);
        when(preferenceService.isChannelAllowed("user-1", Channel.EMAIL)).thenReturn(true);
        when(rateLimiterService.tryConsume("user-1")).thenReturn(true);
        when(templateService.render(eq("welcome"), eq(Channel.EMAIL), any()))
                .thenReturn(new TemplateService.Rendered("Welcome, Umang!", "Hi Umang"));
        when(channelFactory.forChannel(Channel.EMAIL)).thenReturn(sender);
        when(notificationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void sendsAndPersistsSentOnHappyPath() {
        happyPathStubs();

        deliveryService.deliver(event());

        verify(sender).send(eq("user-1"), eq("Welcome, Umang!"), eq("Hi Umang"));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void duplicateKeyResultsInExactlyOneSend() {
        // First event claims the key and sends; the duplicate is dropped by SETNX.
        when(idempotencyService.markIfFirst("idem-1:EMAIL")).thenReturn(true, false);
        when(preferenceService.isChannelAllowed("user-1", Channel.EMAIL)).thenReturn(true);
        when(rateLimiterService.tryConsume("user-1")).thenReturn(true);
        when(templateService.render(eq("welcome"), eq(Channel.EMAIL), any()))
                .thenReturn(new TemplateService.Rendered("s", "b"));
        when(channelFactory.forChannel(Channel.EMAIL)).thenReturn(sender);
        when(notificationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        deliveryService.deliver(event()); // first: sends
        deliveryService.deliver(event()); // duplicate: dropped

        // Effectively-once: exactly one channel send despite two deliveries of the same key.
        verify(sender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void optedOutChannelIsSkippedNotSent() {
        when(idempotencyService.markIfFirst(anyString())).thenReturn(true);
        when(preferenceService.isChannelAllowed("user-1", Channel.EMAIL)).thenReturn(false);
        when(notificationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        deliveryService.deliver(event());

        verifyNoInteractions(channelFactory); // never even resolves a sender
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    }

    @Test
    void rateLimitedRequestIsRecordedAndNotSent() {
        when(idempotencyService.markIfFirst(anyString())).thenReturn(true);
        when(preferenceService.isChannelAllowed("user-1", Channel.EMAIL)).thenReturn(true);
        when(rateLimiterService.tryConsume("user-1")).thenReturn(false); // cap hit
        when(notificationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        deliveryService.deliver(event());

        verify(sender, never()).send(anyString(), anyString(), anyString());
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.RATE_LIMITED);
    }

    @Test
    void transientFailureRethrownSoKafkaRetriesThenDlt() {
        happyPathStubs();
        doThrow(new TransientChannelException("SES down"))
                .when(sender).send(anyString(), any(), any());

        // Rethrown so the container's DefaultErrorHandler retries and finally routes to DLT.
        assertThatThrownBy(() -> deliveryService.deliver(event()))
                .isInstanceOf(TransientChannelException.class);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
}
