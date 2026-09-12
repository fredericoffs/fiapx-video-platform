package com.fiapx.videoapi.application.event;

import java.util.UUID;

/**
 * Contrato do evento de upload. {@code eventId} identifica a publicação (idempotência no
 * consumidor sob reentrega); {@code contractVersion} permite evoluir o formato sem quebrar
 * consumidores antigos.
 */
public record VideoUploadRequestedPayload(
    UUID videoId,
    String storageKey,
    String originalFilename,
    UUID eventId,
    Integer contractVersion
) {

  public static final int CURRENT_CONTRACT_VERSION = 1;

  public VideoUploadRequestedPayload(UUID videoId, String storageKey, String originalFilename) {
    this(videoId, storageKey, originalFilename, null, CURRENT_CONTRACT_VERSION);
  }
}
