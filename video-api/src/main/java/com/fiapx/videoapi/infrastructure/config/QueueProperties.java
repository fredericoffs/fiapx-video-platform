package com.fiapx.videoapi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fiapx.queues")
public record QueueProperties(
    String processing,
    String statusUpdates,
    String notification
) {

}
