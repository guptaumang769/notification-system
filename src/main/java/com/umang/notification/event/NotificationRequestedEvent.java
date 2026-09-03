package com.umang.notification.event;

import com.umang.notification.model.enums.Channel;
import java.time.Instant;
import java.util.Map;

/**
 * The single event that travels the pub/sub backbone (topic {@code notification-requests}).
 *
 * <p>Producers publish this and return immediately — delivery is fully asynchronous. The
 * event is intentionally self-contained (all the consumer needs to render + route) so the
 * consumer never has to call back to the producer. One event carries exactly one channel:
 * the ingestion service fans a multi-channel request into one event per channel, which lets
 * each channel retry, fail, and land in the DLT independently.
 *
 * <p>A Java record is an immutable, Jackson-friendly value type — ideal for a Kafka payload.
 *
 * @param idempotencyKey unique per request+channel; the consumer dedupes on this
 * @param userId         recipient
 * @param eventKey       business event that triggered the notification (e.g. {@code order.shipped})
 * @param channel        the single channel this event targets
 * @param templateKey    which template to render
 * @param params         placeholder values for template substitution
 * @param sendAt         future delivery time, or null for send-now
 * @param timestamp      when the event was created
 */
public record NotificationRequestedEvent(
        String idempotencyKey,
        String userId,
        String eventKey,
        Channel channel,
        String templateKey,
        Map<String, String> params,
        Instant sendAt,
        Instant timestamp) {
}
