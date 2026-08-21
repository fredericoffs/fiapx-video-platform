package com.fiapx.videoapi.domain.port;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository {

  Video save(Video video);

  Optional<Video> findById(UUID id);

  PageResult<Video> findByUserId(UUID userId, VideoStatus statusFilter, int page, int size);

  PageResult<Video> findAll(VideoStatus statusFilter, int page, int size);

  void deleteById(UUID id);
}
