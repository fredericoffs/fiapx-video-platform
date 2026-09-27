package com.fiapx.notificationworker.application.usecase;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.domain.exception.NotificationDeliveryException;
import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import com.fiapx.notificationworker.infrastructure.config.NotificationProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Meu dispatcher: o e-mail é a entrega ao usuário; o webhook é só alerta operacional pra equipe.
 * Se o e-mail falhar (circuito aberto, bulkhead cheio ou erro real de envio), aviso a equipe pelo
 * webhook e relanço mesmo assim — o sucesso do alerta não conclui a notificação, então a
 * reentrega do SQS tenta o e-mail de novo até esgotar e cair na DLQ. O webhook sai uma vez só
 * por vídeo: nas reentregas, a tentativa SENT dele é reconhecida e não repete.
 */
@Service
public class SendFailureNotificationUseCase {

  private static final Logger log = LoggerFactory.getLogger(SendFailureNotificationUseCase.class);

  private final Map<NotificationChannelType, NotificationChannel> channelsByType;
  private final NotificationAttemptRepository notificationAttemptRepository;
  private final Duration channelTimeout;

  public SendFailureNotificationUseCase(
      List<NotificationChannel> channels,
      NotificationAttemptRepository notificationAttemptRepository,
      NotificationProperties notificationProperties
  ) {
    this.channelsByType = channels.stream().collect(Collectors.toMap(NotificationChannel::type, Function.identity()));
    this.notificationAttemptRepository = notificationAttemptRepository;
    this.channelTimeout = notificationProperties.channelTimeout();
  }

  public void handle(NotificationRequestedMessage message) {
    if (tryChannel(NotificationChannelType.EMAIL, message)) {
      return;
    }
    alertOperators(message);
    throw new NotificationDeliveryException(
        "E-mail do vídeo " + message.videoId() + " não entregue ao usuário; reentrega tenta de novo", null);
  }

  // Best-effort: falha ou tentativa em andamento no alerta não muda o desfecho (relançar).
  private void alertOperators(NotificationRequestedMessage message) {
    try {
      tryChannel(NotificationChannelType.WEBHOOK, message);
    } catch (NotificationDeliveryException e) {
      log.warn("Alerta operacional do vídeo {} não confirmado: {}", message.videoId(), e.getMessage());
    }
  }

  // Reivindica ANTES de chamar o canal (não "consultar então enviar"): fecha a corrida em que
  // duas execuções concorrentes (reentrega da fila, ou duas réplicas) passavam pela checagem e
  // mandavam o e-mail/webhook duas vezes antes de qualquer uma registrar sucesso.
  private boolean tryChannel(NotificationChannelType type, NotificationRequestedMessage message) {
    Optional<NotificationAttempt> claim = notificationAttemptRepository.tryClaim(message.videoId(), type);
    if (claim.isEmpty()) {
      if (notificationAttemptRepository.isSent(message.videoId(), type)) {
        return true;
      }
      throw new NotificationDeliveryException("Tentativa em andamento; aguardar reentrega", null);
    }

    NotificationAttempt attempt = claim.get();
    NotificationChannel channel = channelsByType.get(type);
    java.util.concurrent.CompletableFuture<Void> delivery = null;
    try {
      // .get(timeout) em vez de .join(): circuit breaker/bulkhead protegem o canal de
      // sobrecarga, mas nenhum dos dois impõe um prazo de execução — sem isso, um destino que
      // aceita a conexão e nunca responde prenderia este consumidor indefinidamente.
      delivery = channel.send(message.videoId(), message.errorMessage(), message.recipientEmail());
      delivery.get(channelTimeout.toMillis(), TimeUnit.MILLISECONDS);
      notificationAttemptRepository.markSent(attempt.getId());
      // Sucesso também vai pro log: é a evidência que scripts/aws-e2e-smoke.sh procura.
      log.info("Notificação do vídeo {} enviada pelo canal {}", message.videoId(), type);
      return true;
    } catch (TimeoutException e) {
      // Resultado desconhecido: não iniciar outro canal enquanto o primeiro pode entregar.
      delivery.whenComplete((ignored, failure) -> {
        if (failure == null) {
          notificationAttemptRepository.markSent(attempt.getId());
          log.info("Notificação do vídeo {} enviada pelo canal {} (após o prazo)", message.videoId(), type);
        } else {
          notificationAttemptRepository.markFailed(attempt.getId(), failure.getMessage());
        }
      });
      throw new NotificationDeliveryException("Prazo excedido; resultado do canal ainda desconhecido", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      notificationAttemptRepository.markFailed(attempt.getId(), cause.getMessage());
      log.warn("Falha ao notificar vídeo {} pelo canal {}", message.videoId(), type, cause);
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new NotificationDeliveryException("Interrompido; tentativa preservada para recuperação", e);
    } catch (RuntimeException e) {
      notificationAttemptRepository.markFailed(attempt.getId(), e.getMessage());
      return false;
    }
  }
}
