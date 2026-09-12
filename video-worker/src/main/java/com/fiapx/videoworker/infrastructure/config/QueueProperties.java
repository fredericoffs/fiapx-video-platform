package com.fiapx.videoworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.queues")
public record QueueProperties(
    String processing, String statusUpdates,
    @DefaultValue("video.processing.dlx") String processingDlx,
    @DefaultValue("video.processing.dlq") String processingDlq,
    @DefaultValue("video.status-updates.dlx") String statusUpdatesDlx,
    @DefaultValue("video.status-updates.dlq") String statusUpdatesDlq
) {

}
