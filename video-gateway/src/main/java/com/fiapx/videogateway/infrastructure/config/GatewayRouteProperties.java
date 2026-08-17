package com.fiapx.videogateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.gateway")
public record GatewayRouteProperties(
    @DefaultValue("http://localhost:8081") String videoApiUri,
    @NestedConfigurationProperty RateLimit rateLimit
) {

  public record RateLimit(
      @DefaultValue("20") long capacity,
      @DefaultValue("60") long periodSeconds
  ) {

  }
}
