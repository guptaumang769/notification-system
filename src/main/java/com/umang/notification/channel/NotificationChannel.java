package com.umang.notification.channel;

import com.umang.notification.model.enums.Channel;

/**
 * Strategy interface for a delivery channel. Each concrete sender (email/SMS/push) is a
 * Spring bean; the {@code ChannelFactory} selects the right one by {@link Channel} enum.
 *
 * <p>Adding a new channel (e.g. WhatsApp, Slack) is a matter of adding one implementation
 * — no consumer or factory code changes, since the factory discovers beans reflectively.
 */
public interface NotificationChannel {

    /** Which channel this strategy handles. */
    Channel channel();

    /**
     * Deliver a rendered message. Implementations throw {@link TransientChannelException}
     * for retryable failures; a normal return means the channel accepted the message.
     *
     * @param recipient the resolved recipient handle (email/phone/device token)
     * @param subject   rendered subject (may be ignored by channels without one)
     * @param body      rendered body
     */
    void send(String recipient, String subject, String body);
}
