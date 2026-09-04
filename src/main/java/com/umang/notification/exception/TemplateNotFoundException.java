package com.umang.notification.exception;

import com.umang.notification.model.enums.Channel;

/**
 * Permanent (non-retryable) failure: no template exists for the requested key+channel.
 * The consumer records the notification as FAILED without retrying, since retrying a
 * missing template would never succeed.
 */
public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(String templateKey, Channel channel) {
        super("No template found for key '" + templateKey + "' and channel " + channel);
    }
}
