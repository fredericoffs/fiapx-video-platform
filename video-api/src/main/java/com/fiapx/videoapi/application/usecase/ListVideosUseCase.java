package com.fiapx.videoapi.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.VideoRepository;

@Service
public class ListVideosUseCase {

  private final VideoRepository videoRepository;

  public ListVideosUseCase(VideoRepository videoRepository) {
    this.videoRepository = videoRepository;
  }

  public PageResult<Video> handle(UUID userId, VideoStatus statusFilter, int page, int size) {
    return videoRepository.findByUserId(userId, statusFilter, page, size);
  }
}
