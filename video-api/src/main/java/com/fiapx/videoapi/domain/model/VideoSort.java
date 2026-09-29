package com.fiapx.videoapi.domain.model;

public record VideoSort(
    VideoSortField field,
    boolean ascending
) {

  public static final VideoSort NEWEST_FIRST = new VideoSort(VideoSortField.CREATED_AT, false);

  public VideoSort {
    field = field == null ? VideoSortField.CREATED_AT : field;
  }
}
