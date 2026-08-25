package com.fiapx.videogateway.infrastructure.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import com.fiapx.videogateway.infrastructure.correlation.CorrelationIdFilterFunction;
import com.fiapx.videogateway.infrastructure.ratelimit.RateLimitFilterFunction;

@Configuration
public class RoutesConfig {

  @Bean
  public RouterFunction<ServerResponse> videoApiRoutes(
      GatewayRouteProperties properties,
      RateLimitFilterFunction rateLimitFilterFunction,
      CorrelationIdFilterFunction correlationIdFilterFunction
  ) {
    return route("video-api")
        .route(path("/auth/**").or(path("/videos/**")).or(path("/admin/**")), http())
        .before(uri(properties.videoApiUri()))
        // Ordem dos .filter() importa: correlation-id nasce antes do rate limiting.
        .filter(correlationIdFilterFunction)
        .filter(rateLimitFilterFunction)
        .build();
  }
}
