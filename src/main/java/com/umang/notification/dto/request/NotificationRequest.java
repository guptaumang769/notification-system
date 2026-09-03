package com.umang.notification.dto.request;

import com.umang.notification.model.enums.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Inbound API payload for {@code POST /api/v1/notifications}. A single request may target
 * multiple channels; the ingestion service fans it out to one event per channel.
 *
 * @param userId         recipient user id
 * @param eventKey       business event that triggered the send (e.g. {@code order.shipped})
 * @param channels       one or more channels to deliver on
 * @param templateKey    template to render for each channel
 * @param params         placeholder values (e.g. {@code {"name":"Umang"}})
 * @param idempotencyKey caller-supplied dedupe key; same key ⇒ delivered at most once
 * @param sendAt         optional future delivery time (SCHEDULED); null ⇒ send now
 */
public record NotificationRequest(
        @NotBlank String userId,
        @NotBlank String eventKey,
        @NotEmpty List<Channel> channels,
        @NotBlank String templateKey,
        @NotNull Map<String, String> params,
        @NotBlank String idempotencyKey,
        Instant sendAt) {
}
