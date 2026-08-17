package com.fiapx.videoapi.infrastructure.persistence.mapper;

import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;

public final class VideoMapper {

  private VideoMapper() {
  }

  public static VideoEntity toEntity(Video video) {
    VideoEntity entity = new VideoEntity();
    entity.setId(video.getId());
    entity.setUserId(video.getUserId());
    entity.setOriginalFilename(video.getOriginalFilename());
    entity.setStorageKey(video.getStorageKey());
    entity.setZipStorageKey(video.getZipStorageKey());
    entity.setStatus(video.getStatus());
    entity.setErrorMessage(video.getErrorMessage());
    entity.setCreatedAt(video.getCreatedAt());
    entity.setUpdatedAt(video.getUpdatedAt());
    entity.setVersion(video.getVersion());
    return entity;
  }

  public static Video toDomain(VideoEntity entity) {
    return new Video(
        entity.getId(),
        entity.getUserId(),
        entity.getOriginalFilename(),
        entity.getStorageKey(),
        entity.getZipStorageKey(),
        entity.getStatus(),
        entity.getErrorMessage(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getVersion()
    );
  }
}
