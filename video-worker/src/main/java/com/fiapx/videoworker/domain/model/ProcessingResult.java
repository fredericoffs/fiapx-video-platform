package com.fiapx.videoworker.domain.model;

import java.util.UUID;

public class ProcessingResult {

  private final UUID videoId;
  private final boolean success;
  private final String zipStorageKey;
  private final String errorMessage;

  private ProcessingResult(
      UUID videoId,
      boolean success,
      String zipStorageKey,
      String errorMessage
  ) {
    this.videoId = videoId;
    this.success = success;
    this.zipStorageKey = zipStorageKey;
    this.errorMessage = errorMessage;
  }

  public static ProcessingResult success(UUID videoId, String zipStorageKey) {
    return new ProcessingResult(videoId, true, zipStorageKey, null);
  }

  public static ProcessingResult failure(UUID videoId, String errorMessage) {
    return new ProcessingResult(videoId, false, null, errorMessage);
  }

  public UUID getVideoId() {
    return videoId;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getZipStorageKey() {
    return zipStorageKey;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
