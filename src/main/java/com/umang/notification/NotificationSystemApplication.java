package com.umang.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the multi-channel notification platform.
 *
 * <p>{@code @EnableScheduling} powers the {@code ScheduledNotificationPoller}, which
 * promotes due {@code SCHEDULED} notifications onto the Kafka pub/sub backbone.
 */
@SpringBootApplication
@EnableScheduling
public class NotificationSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationSystemApplication.class, args);
    }
}
