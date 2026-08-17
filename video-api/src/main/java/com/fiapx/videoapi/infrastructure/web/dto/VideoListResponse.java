package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import java.util.List;

public record VideoListResponse(
    List<VideoStatusResponse> items,
    int page,
    int size,
    long totalElements
) {

  public static VideoListResponse from(PageResult<Video> result) {
    List<VideoStatusResponse> items = result.items().stream().map(VideoStatusResponse::from).toList();
    return new VideoListResponse(items, result.page(), result.size(), result.totalElements());
  }
}
