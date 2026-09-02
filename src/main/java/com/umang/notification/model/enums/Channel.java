package com.umang.notification.model.enums;

/**
 * Delivery channels. Each maps to a {@link com.umang.notification.channel.NotificationChannel}
 * strategy implementation, selected at runtime by the {@code ChannelFactory}.
 */
public enum Channel {
    EMAIL,
    SMS,
    PUSH
}
