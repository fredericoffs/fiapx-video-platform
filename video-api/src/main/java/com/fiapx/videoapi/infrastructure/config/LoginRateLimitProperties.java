package com.fiapx.videoapi.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.security.login-rate-limit")
public record LoginRateLimitProperties(
    @DefaultValue("5") int maxAttempts,
    @DefaultValue("60") long windowSeconds
) {

}
