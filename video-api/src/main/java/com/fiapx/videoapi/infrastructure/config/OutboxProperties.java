package com.fiapx.videoapi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fiapx.outbox")
public record OutboxProperties(
    long publishIntervalMs,
    int batchSize
) {

}
