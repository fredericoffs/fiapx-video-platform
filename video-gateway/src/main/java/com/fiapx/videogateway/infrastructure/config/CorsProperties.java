package com.fiapx.videogateway.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.gateway.cors")
public record CorsProperties(
    @DefaultValue("*") List<String> allowedOrigins
) {

}
