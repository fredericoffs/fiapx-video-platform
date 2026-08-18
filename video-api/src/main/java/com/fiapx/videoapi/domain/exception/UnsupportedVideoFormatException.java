package com.fiapx.videoapi.domain.exception;

public class UnsupportedVideoFormatException extends RuntimeException {

  public UnsupportedVideoFormatException(String originalFilename) {
    super("Formato de vídeo não suportado: " + originalFilename);
  }
}
