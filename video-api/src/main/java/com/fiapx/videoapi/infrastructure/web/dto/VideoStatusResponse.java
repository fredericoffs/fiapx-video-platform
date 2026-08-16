package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import java.time.Instant;
import java.util.UUID;

public record VideoStatusResponse(
    UUID id,
    String originalFilename,
    VideoStatus status,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {

  public static VideoStatusResponse from(Video video) {
    return new VideoStatusResponse(
        video.getId(),
        video.getOriginalFilename(),
        video.getStatus(),
        video.getErrorMessage(),
        video.getCreatedAt(),
        video.getUpdatedAt()
    );
  }
}
