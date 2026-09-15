package com.fiapx.videoapi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.queues")
public record QueueProperties(
    String processing,
    String statusUpdates,
    String notification,
    @DefaultValue("fiapx-video-processing-dlq") String processingDlq,
    @DefaultValue("fiapx-video-status-updates-dlq") String statusUpdatesDlq,
    @DefaultValue("fiapx-video-notification-dlq") String notificationDlq
) {

}
