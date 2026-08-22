package com.fiapx.videogateway.infrastructure.correlation;

import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class CorrelationIdFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

  public static final String HEADER_NAME = "X-Correlation-Id";

  @Override
  public @NonNull ServerResponse filter(ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
    String incoming = request.headers().firstHeader(HEADER_NAME);
    String correlationId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;
    ServerRequest withCorrelationId = ServerRequest.from(request)
        .headers(headers -> headers.set(HEADER_NAME, correlationId))
        .build();
    return next.handle(withCorrelationId);
  }
}
