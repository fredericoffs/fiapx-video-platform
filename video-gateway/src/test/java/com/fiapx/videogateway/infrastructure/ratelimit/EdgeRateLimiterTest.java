package com.fiapx.videogateway.infrastructure.ratelimit;

import com.fiapx.videogateway.infrastructure.config.GatewayRouteProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EdgeRateLimiterTest {

  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private EdgeRateLimiter rateLimiter;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    GatewayRouteProperties properties =
        new GatewayRouteProperties("http://localhost:8081", new GatewayRouteProperties.RateLimit(3, 60));
    rateLimiter = new EdgeRateLimiter(redisTemplate, properties);
  }

  @Test
  void allowsFirstRequestAndSetsExpiration() {
    when(valueOperations.increment("gateway_rate_limit:client-a")).thenReturn(1L);

    boolean allowed = rateLimiter.tryConsume("client-a");

    assertThat(allowed).isTrue();
    verify(redisTemplate).expire("gateway_rate_limit:client-a", Duration.ofSeconds(60));
  }

  @Test
  void allowsRequestWithinCapacityWithoutResettingExpiration() {
    when(valueOperations.increment("gateway_rate_limit:client-a")).thenReturn(3L);

    boolean allowed = rateLimiter.tryConsume("client-a");

    assertThat(allowed).isTrue();
    verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
  }

  @Test
  void blocksRequestAboveCapacity() {
    when(valueOperations.increment("gateway_rate_limit:client-a")).thenReturn(4L);

    boolean allowed = rateLimiter.tryConsume("client-a");

    assertThat(allowed).isFalse();
  }
}
