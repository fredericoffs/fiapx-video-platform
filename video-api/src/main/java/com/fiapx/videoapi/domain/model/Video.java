package com.fiapx.videoapi.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Video {

  private final UUID id;
  private final UUID userId;
  private final String originalFilename;
  private final String storageKey;
  private final Long fileSizeBytes;
  private String zipStorageKey;
  private VideoStatus status;
  private String errorMessage;
  private final Instant createdAt;
  private Instant updatedAt;
  private final Long version;

  public Video(
      UUID id,
      UUID userId,
      String originalFilename,
      String storageKey,
      Long fileSizeBytes,
      String zipStorageKey,
      VideoStatus status,
      String errorMessage,
      Instant createdAt,
      Instant updatedAt,
      Long version
  ) {
    this.id = id;
    this.userId = userId;
    this.originalFilename = originalFilename;
    this.storageKey = storageKey;
    this.fileSizeBytes = fileSizeBytes;
    this.zipStorageKey = zipStorageKey;
    this.status = status;
    this.errorMessage = errorMessage;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  /**
   * fileSizeBytes é null pra vídeos enfileirados antes da coluna existir (V7) — nunca
   * retroalimentado, só fica null pra sempre nesses registros antigos.
   */
  public static Video newQueued(
      UUID id, UUID userId, String originalFilename, String storageKey, Long fileSizeBytes
  ) {
    Instant now = Instant.now();
    return new Video(
        id, userId, originalFilename, storageKey, fileSizeBytes, null, VideoStatus.QUEUED, null, now, now, null
    );
  }

  public static Video newQueued(UUID id, UUID userId, String originalFilename, String storageKey) {
    return newQueued(id, userId, originalFilename, storageKey, null);
  }

  public boolean belongsTo(UUID requesterId) {
    return userId != null && userId.equals(requesterId);
  }

  /**
   * QUEUED → PROCESSING quando o worker começa. Só sai de QUEUED: um "started" atrasado
   * (depois do resultado) ou repetido não regride nem altera estado terminal.
   */
  public boolean startProcessing() {
    if (status != VideoStatus.QUEUED) {
      return false;
    }
    this.status = VideoStatus.PROCESSING;
    this.updatedAt = Instant.now();
    return true;
  }

  public void complete(String zipStorageKey) {
    this.status = VideoStatus.COMPLETED;
    this.zipStorageKey = zipStorageKey;
    this.errorMessage = null;
    this.updatedAt = Instant.now();
  }

  public void fail(String errorMessage) {
    this.status = VideoStatus.FAILED;
    this.errorMessage = errorMessage;
    this.updatedAt = Instant.now();
  }

  public boolean isTerminal() {
    return status == VideoStatus.COMPLETED || status == VideoStatus.FAILED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public Long getFileSizeBytes() {
    return fileSizeBytes;
  }

  public String getZipStorageKey() {
    return zipStorageKey;
  }

  public VideoStatus getStatus() {
    return status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getVersion() {
    return version;
  }
}
