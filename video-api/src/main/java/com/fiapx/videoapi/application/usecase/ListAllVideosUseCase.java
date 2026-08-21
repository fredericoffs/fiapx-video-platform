package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.VideoRepository;
import org.springframework.stereotype.Service;

@Service
public class ListAllVideosUseCase {

  private final VideoRepository videoRepository;

  public ListAllVideosUseCase(VideoRepository videoRepository) {
    this.videoRepository = videoRepository;
  }

  public PageResult<Video> handle(VideoStatus statusFilter, int page, int size) {
    return videoRepository.findAll(statusFilter, page, size);
  }
}
