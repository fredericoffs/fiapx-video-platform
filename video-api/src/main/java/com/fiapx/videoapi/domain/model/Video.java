package com.fiapx.videoapi.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Video {

  private final UUID id;
  private final String originalFilename;
  private final String storageKey;
  private String zipStorageKey;
  private VideoStatus status;
  private String errorMessage;
  private final Instant createdAt;
  private Instant updatedAt;
  private Long version;

  public Video(
      UUID id,
      String originalFilename,
      String storageKey,
      String zipStorageKey,
      VideoStatus status,
      String errorMessage,
      Instant createdAt,
      Instant updatedAt,
      Long version
  ) {
    this.id = id;
    this.originalFilename = originalFilename;
    this.storageKey = storageKey;
    this.zipStorageKey = zipStorageKey;
    this.status = status;
    this.errorMessage = errorMessage;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  public static Video newQueued(UUID id, String originalFilename, String storageKey) {
    Instant now = Instant.now();
    return new Video(id, originalFilename, storageKey, null, VideoStatus.QUEUED, null, now, now, null);
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

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getStorageKey() {
    return storageKey;
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
