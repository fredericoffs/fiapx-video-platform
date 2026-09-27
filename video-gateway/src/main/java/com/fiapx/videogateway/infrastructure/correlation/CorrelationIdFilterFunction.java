package com.fiapx.videogateway.infrastructure.correlation;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Início do rastro: reaproveita ou gera o correlationId, repassa ao video-api no header,
 * coloca no MDC (sai em todo log JSON deste serviço) e devolve na resposta, para quem chamou
 * poder buscar o mesmo id nos logs de todos os serviços (Grafana → Logs da aplicação).
 */
@Component
public class CorrelationIdFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

  public static final String HEADER_NAME = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilterFunction.class);

  @Override
  public @NonNull ServerResponse filter(ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
    String incoming = request.headers().firstHeader(HEADER_NAME);
    String correlationId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;
    ServerRequest withCorrelationId = ServerRequest.from(request)
        .headers(headers -> headers.set(HEADER_NAME, correlationId))
        .build();
    MDC.put(MDC_KEY, correlationId);
    long start = System.nanoTime();
    try {
      exposeCorrelationId(correlationId);
      ServerResponse response = next.handle(withCorrelationId);
      logRequest(request, response.statusCode().value(), start);
      return response;
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  // Escritas e erros sempre; GET com sucesso não — o polling de status do web geraria uma
  // linha a cada poucos segundos por usuário, e o ingress já registra o acesso.
  private static void logRequest(ServerRequest request, int status, long start) {
    if (HttpMethod.GET.equals(request.method()) && status < 400) {
      return;
    }
    log.info("{} {} -> {} ({} ms)", request.method(), request.path(), status,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
  }

  // Os headers de um ServerResponse já construído são imutáveis; escrevo na resposta do
  // servlet, que o gateway completa depois com os headers vindos do video-api.
  private static void exposeCorrelationId(String correlationId) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
        && attributes.getResponse() != null) {
      attributes.getResponse().setHeader(HEADER_NAME, correlationId);
    }
  }
}
