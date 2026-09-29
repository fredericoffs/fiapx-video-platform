package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.VideoWithOwner;
import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoFilter;
import com.fiapx.videoapi.domain.model.VideoSort;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListAllVideosUseCase {

  private final VideoRepository videoRepository;
  private final UserRepository userRepository;

  public ListAllVideosUseCase(VideoRepository videoRepository, UserRepository userRepository) {
    this.videoRepository = videoRepository;
    this.userRepository = userRepository;
  }

  public PageResult<VideoWithOwner> handle(VideoFilter filter, VideoSort sort, int page, int size) {
    PageResult<Video> result = videoRepository.findAll(filter, sort, page, size);
    List<VideoWithOwner> items = result.items().stream()
        .map(video -> new VideoWithOwner(video, resolveOwnerEmail(video)))
        .toList();
    return new PageResult<>(items, result.page(), result.size(), result.totalElements());
  }

  private String resolveOwnerEmail(Video video) {
    if (video.getUserId() == null) {
      return null;
    }
    return userRepository.findById(video.getUserId()).map(User::getEmail).orElse(null);
  }
}
