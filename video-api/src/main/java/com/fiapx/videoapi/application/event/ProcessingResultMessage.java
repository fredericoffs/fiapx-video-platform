package com.fiapx.videoapi.application.event;

import java.util.UUID;

/** Evento vindo do video-worker. {@code eventId} é o do pedido de processamento original (rastreio/idempotência). */
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
