package com.fiapx.videoworker.domain.exception;

/** O broker não confirmou a publicação (nack, mensagem devolvida ou prazo esgotado). */
public class MessagePublishException extends RuntimeException {

  public MessagePublishException(String message) {
    super(message);
  }

  public MessagePublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
