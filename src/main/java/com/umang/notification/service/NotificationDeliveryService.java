package com.umang.notification.service;

import com.umang.notification.channel.NotificationChannel;
import com.umang.notification.channel.ChannelFactory;
import com.umang.notification.channel.TransientChannelException;
import com.umang.notification.event.NotificationRequestedEvent;
import com.umang.notification.exception.TemplateNotFoundException;
import com.umang.notification.model.entity.Notification;
import com.umang.notification.model.enums.NotificationStatus;
import com.umang.notification.repository.NotificationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The delivery pipeline invoked by the Kafka consumer for a single event/channel. Ordered
 * gates before the actual send:
 *
 * <ol>
 *   <li><b>Idempotency</b> — {@code SETNX} the key; a duplicate event is dropped (no send,
 *       no duplicate row), giving effectively-once semantics on top of at-least-once Kafka.</li>
 *   <li><b>Preferences</b> — skip (record SKIPPED) if the user opted out of the channel.</li>
 *   <li><b>Rate limit</b> — reject (record RATE_LIMITED) if the user's per-window cap is hit.</li>
 *   <li><b>Render</b> — substitute params into the template (missing template ⇒ permanent FAILED).</li>
 *   <li><b>Send</b> — via the channel Strategy; transient failure re-thrown so Kafka retries
 *       and, once exhausted, the DefaultErrorHandler routes the record to the DLT.</li>
 * </ol>
 *
 * <p>{@link TransientChannelException} is deliberately allowed to propagate out of
 * {@link #deliver}; all other outcomes are persisted and the method returns normally so the
 * offset commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final IdempotencyService idempotencyService;
    private final PreferenceService preferenceService;
    private final RateLimiterService rateLimiterService;
    private final TemplateService templateService;
    private final ChannelFactory channelFactory;
    private final NotificationRepository notificationRepository;

    /**
     * Process one event. Throws {@link TransientChannelException} for retryable send
     * failures (so the caller/Kafka retries); returns normally for every terminal outcome.
     */
    public void deliver(NotificationRequestedEvent event) {
        // 1. Idempotency — the first caller to claim the key proceeds; duplicates are dropped.
        if (!idempotencyService.markIfFirst(event.idempotencyKey())) {
            log.info("Duplicate event dropped (key {})", event.idempotencyKey());
            return;
        }

        // 2. Preferences — respect the user's per-channel opt-out.
        if (!preferenceService.isChannelAllowed(event.userId(), event.channel())) {
            record(event, NotificationStatus.SKIPPED, "User opted out of " + event.channel(), 1, null, null);
            log.info("Skipped {} for user {} (opted out)", event.channel(), event.userId());
            return;
        }

        // 3. Rate limit — protect the user from being flooded.
        if (!rateLimiterService.tryConsume(event.userId())) {
            record(event, NotificationStatus.RATE_LIMITED, "Per-user rate limit exceeded", 1, null, null);
            return;
        }

        // 4. Render the template (permanent failure if the template is missing).
        TemplateService.Rendered rendered;
        try {
            rendered = templateService.render(event.templateKey(), event.channel(), event.params());
        } catch (TemplateNotFoundException ex) {
            record(event, NotificationStatus.FAILED, ex.getMessage(), 1, null, null);
            log.error("Permanent failure: {}", ex.getMessage());
            return; // non-retryable — do NOT rethrow
        }

        // 5. Send via the channel Strategy.
        NotificationChannel sender = channelFactory.forChannel(event.channel());
        try {
            sender.send(event.userId(), rendered.subject(), rendered.body());
            record(event, NotificationStatus.SENT, null, 1, rendered, Instant.now());
            log.info("Delivered {} to user {}", event.channel(), event.userId());
        } catch (TransientChannelException ex) {
            // Record the FAILED attempt, then rethrow so Kafka retries → DLT on exhaustion.
            record(event, NotificationStatus.FAILED, ex.getMessage(), 1, rendered, null);
            throw ex;
        }
    }

    /**
     * Upsert the notification row for this event/channel keyed on the idempotency key, so a
     * retry updates the same row (and the unique constraint is never violated).
     */
    private void record(NotificationRequestedEvent event, NotificationStatus status,
                        String failureReason, int attemptDelta,
                        TemplateService.Rendered rendered, Instant sentAt) {
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
                        .status(NotificationStatus.QUEUED)
                        .build());
        n.setStatus(status);
        n.setFailureReason(failureReason);
        n.setAttempts(n.getAttempts() + attemptDelta);
        if (rendered != null) {
            n.setRenderedSubject(rendered.subject());
            n.setRenderedBody(rendered.body());
        }
        if (sentAt != null) {
            n.setSentAt(sentAt);
        }
        notificationRepository.save(n);
    }
}
