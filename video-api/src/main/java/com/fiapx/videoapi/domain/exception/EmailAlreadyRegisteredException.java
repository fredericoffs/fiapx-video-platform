package com.fiapx.videoapi.domain.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

  public EmailAlreadyRegisteredException(String email) {
    super("E-mail já cadastrado: " + email);
  }
}
