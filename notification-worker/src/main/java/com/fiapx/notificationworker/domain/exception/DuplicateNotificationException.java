package com.fiapx.notificationworker.domain.exception;

import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import java.util.UUID;

/** Já existe envio registrado para este vídeo e canal (índice único parcial) — reentrega tratada como sucesso. */
public class DuplicateNotificationException extends RuntimeException {

  public DuplicateNotificationException(UUID videoId, NotificationChannelType channel) {
    super("Notificação por " + channel + " já registrada como enviada para o vídeo " + videoId);
  }
}
