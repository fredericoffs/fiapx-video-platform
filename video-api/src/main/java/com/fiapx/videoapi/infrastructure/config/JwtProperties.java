package com.fiapx.videoapi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fiapx.jwt")
public record JwtProperties(
    String secret,
    long expirationMinutes
) {

}
