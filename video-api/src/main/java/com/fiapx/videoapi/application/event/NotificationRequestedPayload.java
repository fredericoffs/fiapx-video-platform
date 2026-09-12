package com.fiapx.videoapi.application.event;

import java.util.UUID;

public record NotificationRequestedPayload(
    UUID videoId,
    String errorMessage,
    String recipientEmail,
    UUID eventId,
    Integer contractVersion
) {

  public static final int CURRENT_CONTRACT_VERSION = 1;

  public NotificationRequestedPayload(UUID videoId, String errorMessage, String recipientEmail) {
    this(videoId, errorMessage, recipientEmail, null, CURRENT_CONTRACT_VERSION);
  }
}
