package com.fiapx.videoapi.domain.exception;

/** O broker não confirmou a publicação (nack, mensagem devolvida ou prazo esgotado) — o evento deve permanecer pendente. */
public class MessagePublishException extends RuntimeException {

  public MessagePublishException(String message) {
    super(message);
  }

  public MessagePublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
