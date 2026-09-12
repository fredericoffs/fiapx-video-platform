package com.fiapx.videoworker.infrastructure.messaging.dto;

import java.util.UUID;

/** {@code eventId} é o do pedido de processamento original, propagado para rastreio/idempotência. */
public record ProcessingResultMessage(
    ProcessingEventType eventType,
    UUID videoId,
    String zipStorageKey,
    String errorMessage,
    UUID eventId
) {

  public ProcessingResultMessage(ProcessingEventType eventType, UUID videoId, String zipStorageKey, String errorMessage) {
    this(eventType, videoId, zipStorageKey, errorMessage, null);
  }
}
