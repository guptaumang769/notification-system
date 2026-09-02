package com.umang.notification.model.enums;

/**
 * Lifecycle of a single notification row.
 *
 * <pre>
 *   QUEUED ─────► SENT
 *      │  ╲         (channel accepted the message)
 *      │   ╲──────► FAILED        (transient retries exhausted → DLT)
 *      │   ╲──────► RATE_LIMITED  (per-user cap tripped)
 *      │   ╲──────► SKIPPED       (user opted out of the channel)
 *   SCHEDULED ──► QUEUED          (poller promotes a due future send)
 * </pre>
 */
public enum NotificationStatus {
    SCHEDULED,
    QUEUED,
    SENT,
    FAILED,
    RATE_LIMITED,
    SKIPPED
}
