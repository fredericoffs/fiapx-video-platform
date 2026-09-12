package com.fiapx.videogateway.infrastructure.ratelimit;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.fiapx.videogateway.infrastructure.config.GatewayRouteProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterFunctionTest {

  private EdgeRateLimiter rateLimiter;
  private RateLimitFilterFunction filterFunction;
  private ServerRequest request;
  private HandlerFunction<ServerResponse> next;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    rateLimiter = mock(EdgeRateLimiter.class);
    GatewayRouteProperties properties = new GatewayRouteProperties(
        "http://localhost:8081", new GatewayRouteProperties.RateLimit(20, 60));
    filterFunction = new RateLimitFilterFunction(rateLimiter, properties);
    request = mock(ServerRequest.class);
    next = mock(HandlerFunction.class);
    when(request.remoteAddress()).thenReturn(Optional.of(new InetSocketAddress("192.168.0.10", 54321)));
    headers = mock(ServerRequest.Headers.class);
    when(request.headers()).thenReturn(headers);
    when(headers.firstHeader(RateLimitFilterFunction.FORWARDED_FOR)).thenReturn(null);
  }

  private ServerRequest.Headers headers;

  @Test
  void usesFirstForwardedForAddressWhenBehindAProxy() throws Exception {
    when(headers.firstHeader(RateLimitFilterFunction.FORWARDED_FOR)).thenReturn("203.0.113.7, 10.30.2.1");
    when(rateLimiter.tryConsume("203.0.113.7")).thenReturn(true);
    ServerResponse expectedResponse = ServerResponse.ok().build();
    when(next.handle(request)).thenReturn(expectedResponse);

    ServerResponse response = filterFunction.filter(request, next);

    assertThat(response).isSameAs(expectedResponse);
    verify(rateLimiter, never()).tryConsume("192.168.0.10");
  }

  @Test
  void ignoresBlankForwardedForHeader() throws Exception {
    when(headers.firstHeader(RateLimitFilterFunction.FORWARDED_FOR)).thenReturn(" , ");
    when(rateLimiter.tryConsume("192.168.0.10")).thenReturn(true);
    when(next.handle(request)).thenReturn(ServerResponse.ok().build());

    filterFunction.filter(request, next);

    verify(rateLimiter).tryConsume("192.168.0.10");
  }

  @Test
  void delegatesToNextHandlerWhenWithinLimit() throws Exception {
    when(rateLimiter.tryConsume("192.168.0.10")).thenReturn(true);
    ServerResponse expectedResponse = ServerResponse.ok().build();
    when(next.handle(request)).thenReturn(expectedResponse);

    ServerResponse response = filterFunction.filter(request, next);

    assertThat(response).isSameAs(expectedResponse);
  }

  @Test
  void returnsTooManyRequestsWhenLimitExceeded() throws Exception {
    when(rateLimiter.tryConsume("192.168.0.10")).thenReturn(false);

    ServerResponse response = filterFunction.filter(request, next);

    assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.headers().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    verify(next, never()).handle(request);
  }

  @Test
  void fallsBackToUnknownClientKeyWhenRemoteAddressMissing() throws Exception {
    when(request.remoteAddress()).thenReturn(Optional.empty());
    when(rateLimiter.tryConsume("unknown")).thenReturn(true);
    ServerResponse expectedResponse = ServerResponse.ok().build();
    when(next.handle(request)).thenReturn(expectedResponse);

    ServerResponse response = filterFunction.filter(request, next);

    assertThat(response).isSameAs(expectedResponse);
  }
}
