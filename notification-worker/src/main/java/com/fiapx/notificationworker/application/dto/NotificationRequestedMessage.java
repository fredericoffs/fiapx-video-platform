package com.fiapx.notificationworker.application.dto;

import java.util.UUID;

/**
 * eventId/contractVersion podem vir nulos de produtores antigos.
 */
public record NotificationRequestedMessage(
    UUID videoId,
    String errorMessage,
    String recipientEmail,
    UUID eventId,
    Integer contractVersion
) {

  public NotificationRequestedMessage(UUID videoId, String errorMessage, String recipientEmail) {
    this(videoId, errorMessage, recipientEmail, null, 1);
  }
}
