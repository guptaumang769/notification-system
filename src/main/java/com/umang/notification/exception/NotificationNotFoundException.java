package com.umang.notification.exception;

/** Thrown when a notification id is not found; mapped to HTTP 404. */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long id) {
        super("Notification not found: " + id);
    }
}
