package com.fiapx.videoapi.domain.exception;

public class InvalidFilenameException extends RuntimeException {

  public InvalidFilenameException(String reason) {
    super("Nome de arquivo inválido: " + reason);
  }
}
