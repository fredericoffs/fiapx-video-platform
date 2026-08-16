package com.fiapx.videoapi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fiapx.storage")
public record StorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucketRaw,
    String bucketProcessed
) {

}
