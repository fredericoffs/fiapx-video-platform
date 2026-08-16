package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.domain.model.VideoStatus;
import java.util.UUID;

public record VideoUploadResponse(
    UUID id,
    VideoStatus status
) {

  public static VideoUploadResponse from(VideoUploadResult result) {
    return new VideoUploadResponse(result.id(), result.status());
  }
}
