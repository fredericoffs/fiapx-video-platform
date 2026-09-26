package com.fiapx.videoapi.domain.exception;

import java.util.UUID;

public class VideoBeingProcessedException extends RuntimeException {

  public VideoBeingProcessedException(UUID videoId) {
    super("Vídeo ainda em processamento, não pode ser excluído agora: " + videoId);
  }
}
