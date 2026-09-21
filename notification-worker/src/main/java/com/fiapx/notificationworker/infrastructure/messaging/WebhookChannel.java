package com.fiapx.notificationworker.infrastructure.messaging;

import com.fiapx.notificationworker.domain.exception.NotificationDeliveryException;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import com.fiapx.notificationworker.infrastructure.config.NotificationProperties;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WebhookChannel implements NotificationChannel {

  private final RestClient restClient;
  private final NotificationProperties notificationProperties;

  public WebhookChannel(RestClient.Builder restClientBuilder, NotificationProperties notificationProperties) {
    this.restClient = restClientBuilder.build();
    this.notificationProperties = notificationProperties;
  }

  @Override
  public NotificationChannelType type() {
    return NotificationChannelType.WEBHOOK;
  }

  @Override
  @CircuitBreaker(name = "webhook-channel", fallbackMethod = "unavailable")
  @Bulkhead(name = "webhook-channel", type = Bulkhead.Type.THREADPOOL)
  public CompletableFuture<Void> send(UUID videoId, String errorMessage, String recipientEmail) {
    String url = notificationProperties.webhookFallbackUrl();
    if (url == null || url.isBlank()) {
      throw new NotificationDeliveryException("Nenhum NOTIFICATION_WEBHOOK_URL configurado", null);
    }

    try {
      restClient.post()
          .uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          // Registro (record), não Map.of: recipientEmail nunca deveria vir nulo em produção
          // (SendFailureNotificationUseCase sempre propaga o do evento), mas Map.of lançaria
          // NullPointerException se algum dia vier — um record serializa null sem quebrar.
          .body(new WebhookPayload(videoId.toString(), errorMessage, recipientEmail))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new NotificationDeliveryException("Falha ao chamar o webhook para o vídeo " + videoId, e);
    }

    return CompletableFuture.completedFuture(null);
  }

  // Traduzo qualquer falha do canal (circuito aberto, bulkhead cheio, erro HTTP) pra
  // uma única exceção de domínio — mesmo papel do fallback equivalente no EmailChannel.
  private CompletableFuture<Void> unavailable(UUID videoId, String errorMessage, String recipientEmail, Throwable t) {
    return CompletableFuture.failedFuture(
        new NotificationDeliveryException("Canal de webhook indisponível para o vídeo " + videoId, t));
  }

  // O destinatário precisa estar no payload pra quem recebe o webhook conseguir notificar o
  // dono de verdade — antes desta correção o corpo só tinha videoId/errorMessage.
  private record WebhookPayload(String videoId, String errorMessage, String recipientEmail) {

  }
}
