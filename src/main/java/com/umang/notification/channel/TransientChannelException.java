package com.umang.notification.channel;

/**
 * Thrown by a {@link NotificationChannel} when a send fails in a way that is worth
 * retrying (network blip, downstream 5xx, throttling). The Kafka consumer lets this
 * propagate so {@code DefaultErrorHandler} retries with backoff and, once exhausted,
 * routes the record to the DLT. Permanent failures (bad address, unknown template)
 * should be recorded as {@code FAILED} without throwing, so they are not retried.
 */
public class TransientChannelException extends RuntimeException {

    public TransientChannelException(String message) {
        super(message);
    }
}
