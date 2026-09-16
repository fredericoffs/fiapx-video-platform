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
 * Meu dispatcher: tento o canal primário (e-mail); se falhar (circuito aberto, bulkhead
 * cheio ou erro real de envio), caio pro canal secundário (webhook). Só relanço — pro
 * retry/DLQ do AMQP agir — se os dois canais falharem.
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
    if (tryChannel(NotificationChannelType.WEBHOOK, message)) {
      return;
    }
    throw new NotificationDeliveryException(
        "Falha ao notificar vídeo " + message.videoId() + " por todos os canais", null);
  }

  // Reivindica ANTES de chamar o canal (não "consultar então enviar"): fecha a corrida em que
  // duas execuções concorrentes (reentrega da fila, ou duas réplicas) passavam pela checagem e
  // mandavam o e-mail/webhook duas vezes antes de qualquer uma registrar sucesso.
  private boolean tryChannel(NotificationChannelType type, NotificationRequestedMessage message) {
    Optional<NotificationAttempt> claim = notificationAttemptRepository.tryClaim(message.videoId(), type);
    if (claim.isEmpty()) {
      log.info("Notificação por {} já enviada ou em andamento para o vídeo {}, ignorando (idempotência)", type,
          message.videoId());
      return true;
    }

    NotificationAttempt attempt = claim.get();
    NotificationChannel channel = channelsByType.get(type);
    try {
      // .get(timeout) em vez de .join(): circuit breaker/bulkhead protegem o canal de
      // sobrecarga, mas nenhum dos dois impõe um prazo de execução — sem isso, um destino que
      // aceita a conexão e nunca responde prenderia este consumidor indefinidamente.
      channel.send(message.videoId(), message.errorMessage(), message.recipientEmail())
          .get(channelTimeout.toMillis(), TimeUnit.MILLISECONDS);
      notificationAttemptRepository.markSent(attempt.getId());
      return true;
    } catch (TimeoutException e) {
      notificationAttemptRepository.markFailed(attempt.getId(),
          "Canal " + type + " não respondeu em " + channelTimeout);
      log.warn("Timeout de {} aguardando o canal {} pro vídeo {}", channelTimeout, type, message.videoId());
      return false;
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      notificationAttemptRepository.markFailed(attempt.getId(), cause.getMessage());
      log.warn("Falha ao notificar vídeo {} pelo canal {}", message.videoId(), type, cause);
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      notificationAttemptRepository.markFailed(attempt.getId(), "Interrompido aguardando o canal " + type);
      log.warn("Interrompido aguardando o canal {} pro vídeo {}", type, message.videoId(), e);
      return false;
    }
  }
}
