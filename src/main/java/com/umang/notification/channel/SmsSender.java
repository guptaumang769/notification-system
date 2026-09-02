package com.umang.notification.channel;

import com.umang.notification.model.enums.Channel;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MOCK SMS channel. A real implementation would call AWS SNS (or Twilio) here.
 * SMS has no subject, so it is ignored. Simulates occasional transient failure.
 */
@Slf4j
@Component
public class SmsSender implements NotificationChannel {

    private final double transientFailureRate;

    public SmsSender(@Value("${channel.transient-failure-rate:0.0}") double transientFailureRate) {
        this.transientFailureRate = transientFailureRate;
    }

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (ThreadLocalRandom.current().nextDouble() < transientFailureRate) {
            throw new TransientChannelException("SNS throttled for " + recipient);
        }
        // Real impl: snsClient.publish(...)
        log.info("SMS sent to {} | body='{}'", recipient, body);
    }
}
