package com.umang.notification.channel;

import com.umang.notification.model.enums.Channel;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MOCK email channel. A real implementation would call AWS SES (or SendGrid/SMTP) here.
 *
 * <p>To exercise the retry/DLQ path, it simulates an occasional transient failure with
 * probability {@code channel.transient-failure-rate} (default 0 so tests are deterministic;
 * bump it in {@code application.yml} to watch retries + the DLT in action).
 */
@Slf4j
@Component
public class EmailSender implements NotificationChannel {

    private final double transientFailureRate;

    public EmailSender(@Value("${channel.transient-failure-rate:0.0}") double transientFailureRate) {
        this.transientFailureRate = transientFailureRate;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (ThreadLocalRandom.current().nextDouble() < transientFailureRate) {
            throw new TransientChannelException("SES temporarily unavailable for " + recipient);
        }
        // Real impl: sesClient.sendEmail(...)
        log.info("EMAIL sent to {} | subject='{}' | body='{}'", recipient, subject, body);
    }
}
