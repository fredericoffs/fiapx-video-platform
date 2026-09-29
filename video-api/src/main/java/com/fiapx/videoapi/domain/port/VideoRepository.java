package com.fiapx.videoapi.domain.port;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoFilter;
import com.fiapx.videoapi.domain.model.VideoSort;
import com.fiapx.videoapi.domain.model.VideoStatus;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository {

  Video save(Video video);

  Optional<Video> findById(UUID id);

  PageResult<Video> findByUserId(UUID userId, VideoFilter filter, VideoSort sort, int page, int size);

  default PageResult<Video> findByUserId(UUID userId, VideoStatus statusFilter, int page, int size) {
    return findByUserId(userId, VideoFilter.byStatus(statusFilter), VideoSort.NEWEST_FIRST, page, size);
  }

  PageResult<Video> findAll(VideoFilter filter, VideoSort sort, int page, int size);

  void deleteById(UUID id);
}
