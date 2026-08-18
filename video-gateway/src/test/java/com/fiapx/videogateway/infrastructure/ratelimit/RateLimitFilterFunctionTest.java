package com.fiapx.videogateway.infrastructure.ratelimit;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

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
    filterFunction = new RateLimitFilterFunction(rateLimiter);
    request = mock(ServerRequest.class);
    next = mock(HandlerFunction.class);
    when(request.remoteAddress()).thenReturn(Optional.of(new InetSocketAddress("192.168.0.10", 54321)));
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
