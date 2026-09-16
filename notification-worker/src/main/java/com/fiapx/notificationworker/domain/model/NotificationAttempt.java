package com.fiapx.notificationworker.domain.model;

import java.time.Instant;
import java.util.UUID;

public class NotificationAttempt {

  private final UUID id;
  private final UUID videoId;
  private final NotificationChannelType channel;
  private final NotificationStatus status;
  private final String errorMessage;
  private final Instant createdAt;

  public NotificationAttempt(
      UUID id,
      UUID videoId,
      NotificationChannelType channel,
      NotificationStatus status,
      String errorMessage,
      Instant createdAt
  ) {
    this.id = id;
    this.videoId = videoId;
    this.channel = channel;
    this.status = status;
    this.errorMessage = errorMessage;
    this.createdAt = createdAt;
  }

  /**
   * Reivindica o envio antes do canal ser chamado: o índice único parcial (video_id, channel)
   * WHERE status IN ('SENDING', 'SENT') garante que só uma execução consegue inserir essa linha
   * por vez, fechando a corrida entre "consultar se já enviou" e "enviar de fato".
   */
  public static NotificationAttempt claiming(UUID videoId, NotificationChannelType channel) {
    return new NotificationAttempt(
        UUID.randomUUID(), videoId, channel, NotificationStatus.SENDING, null, Instant.now());
  }

  public UUID getId() {
    return id;
  }

  public UUID getVideoId() {
    return videoId;
  }

  public NotificationChannelType getChannel() {
    return channel;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
