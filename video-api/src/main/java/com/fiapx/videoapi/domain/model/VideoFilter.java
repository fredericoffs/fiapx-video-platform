package com.fiapx.videoapi.domain.model;

import java.time.Instant;

/**
 * Todos os campos são opcionais (null = sem filtro). createdFrom/createdTo são inclusivos.
 */
public record VideoFilter(
    VideoStatus status,
    String filename,
    Instant createdFrom,
    Instant createdTo
) {

  public static final VideoFilter NONE = new VideoFilter(null, null, null, null);

  public static VideoFilter byStatus(VideoStatus status) {
    return new VideoFilter(status, null, null, null);
  }
}
