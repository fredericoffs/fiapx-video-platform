package com.fiapx.videoapi.domain.exception;

public class InvalidCurrentPasswordException extends RuntimeException {

  public InvalidCurrentPasswordException() {
    super("Senha atual inválida");
  }
}
