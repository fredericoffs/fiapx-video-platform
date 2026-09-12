package com.fiapx.videoapi.infrastructure.messaging.sqs;

/** Corpo que nunca vai passar em retry (JSON inválido): o consumer move direto para a DLQ. */
public class MalformedMessageException extends RuntimeException {

  public MalformedMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}
