package com.fiapx.videoworker.application.dto;

import java.util.UUID;

/** Contrato do evento de upload; eventId/contractVersion podem vir nulos de produtores antigos. */
public record VideoUploadRequestedPayload(
    UUID videoId,
    String storageKey,
    String originalFilename,
    UUID eventId,
    Integer contractVersion
) {

  public VideoUploadRequestedPayload(UUID videoId, String storageKey, String originalFilename) {
    this(videoId, storageKey, originalFilename, null, 1);
  }
}
