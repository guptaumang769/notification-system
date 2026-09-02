package com.umang.notification.channel;

import com.umang.notification.model.enums.Channel;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MOCK push channel. A real implementation would call Firebase Cloud Messaging (FCM)
 * or APNs here, keyed by the device token. Simulates occasional transient failure.
 */
@Slf4j
@Component
public class PushSender implements NotificationChannel {

    private final double transientFailureRate;

    public PushSender(@Value("${channel.transient-failure-rate:0.0}") double transientFailureRate) {
        this.transientFailureRate = transientFailureRate;
    }

    @Override
    public Channel channel() {
        return Channel.PUSH;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (ThreadLocalRandom.current().nextDouble() < transientFailureRate) {
            throw new TransientChannelException("FCM upstream error for device " + recipient);
        }
        // Real impl: fcmClient.send(Message.builder()...)
        log.info("PUSH sent to device {} | title='{}' | body='{}'", recipient, subject, body);
    }
}
