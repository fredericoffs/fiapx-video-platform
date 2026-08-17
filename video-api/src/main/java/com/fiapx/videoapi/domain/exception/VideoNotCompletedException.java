package com.fiapx.videoapi.domain.exception;

import java.util.UUID;

public class VideoNotCompletedException extends RuntimeException {

  public VideoNotCompletedException(UUID videoId) {
    super("Vídeo ainda não processado: " + videoId);
  }
}
