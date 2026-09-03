package com.umang.notification.dto.response;

import java.util.List;

/**
 * Returned (HTTP 202 Accepted) when a request is published to the pub/sub backbone.
 * The API is fire-and-forget: {@code accepted} true means the events were published,
 * not that delivery succeeded — callers poll {@code GET /api/v1/notifications/{id}}
 * (or the user history) for terminal status.
 *
 * @param idempotencyKey  echo of the caller's key
 * @param channels        channels the request was fanned out to
 * @param accepted        whether the events were published (false ⇒ deduped, already seen)
 * @param message         human-readable status
 */
public record NotificationAccepted(
        String idempotencyKey,
        List<String> channels,
        boolean accepted,
        String message) {
}
