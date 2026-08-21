package com.fiapx.notificationworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.queues")
public record QueueProperties(
    String notification,
    @DefaultValue("video.notification.dlx") String notificationDlx,
    @DefaultValue("video.notification.dlq") String notificationDlq
) {

}
