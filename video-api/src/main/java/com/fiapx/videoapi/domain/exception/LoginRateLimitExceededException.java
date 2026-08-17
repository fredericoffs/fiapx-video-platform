package com.fiapx.videoapi.domain.exception;

public class LoginRateLimitExceededException extends RuntimeException {

  public LoginRateLimitExceededException(String email) {
    super("Muitas tentativas de login para " + email + ". Tente novamente mais tarde.");
  }
}
