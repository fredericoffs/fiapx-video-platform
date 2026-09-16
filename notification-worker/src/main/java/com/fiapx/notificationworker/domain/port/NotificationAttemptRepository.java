package com.fiapx.notificationworker.domain.port;

import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import java.util.Optional;
import java.util.UUID;

public interface NotificationAttemptRepository {

  /**
   * Tenta reivindicar o envio deste vídeo/canal (INSERT de uma linha SENDING). Vazio se já
   * havia uma reivindicação em andamento ou um envio concluído para o mesmo vídeo/canal —
   * reentrega tratada como idempotente, sem chamar o canal de novo.
   */
  Optional<NotificationAttempt> tryClaim(UUID videoId, NotificationChannelType channel);

  void markSent(UUID attemptId);

  void markFailed(UUID attemptId, String errorMessage);
}
