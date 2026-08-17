package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.exception.VideoNotFoundException;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetVideoStatusUseCase {

  private final VideoRepository videoRepository;

  public GetVideoStatusUseCase(VideoRepository videoRepository) {
    this.videoRepository = videoRepository;
  }

  public Video handle(UUID videoId, UUID requesterId) {
    Video video = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
    if (!video.belongsTo(requesterId)) {
      throw new VideoNotFoundException(videoId);
    }
    return video;
  }
}
