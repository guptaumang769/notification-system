package com.umang.notification.channel;

import com.umang.notification.model.enums.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Factory for the channel Strategy. Spring injects every {@link NotificationChannel} bean;
 * we index them by their {@link Channel} enum so the consumer can look up the right sender
 * in O(1). New channels register themselves simply by being Spring beans — no edits here.
 */
@Component
public class ChannelFactory {

    private final Map<Channel, NotificationChannel> byChannel = new EnumMap<>(Channel.class);

    public ChannelFactory(List<NotificationChannel> channels) {
        for (NotificationChannel c : channels) {
            byChannel.put(c.channel(), c);
        }
    }

    /**
     * @throws IllegalArgumentException if no strategy is registered for the channel
     */
    public NotificationChannel forChannel(Channel channel) {
        NotificationChannel c = byChannel.get(channel);
        if (c == null) {
            throw new IllegalArgumentException("No sender registered for channel " + channel);
        }
        return c;
    }
}
