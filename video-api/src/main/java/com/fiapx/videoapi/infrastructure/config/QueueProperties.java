package com.fiapx.videoapi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.queues")
public record QueueProperties(
    String processing,
    String statusUpdates,
    String notification,
    @DefaultValue("video.processing.dlx") String processingDlx,
    @DefaultValue("video.processing.dlq") String processingDlq,
    @DefaultValue("video.notification.dlx") String notificationDlx,
    @DefaultValue("video.notification.dlq") String notificationDlq
) {

}
