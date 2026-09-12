package com.fiapx.videoworker.domain.exception;

/** Entrada que não pode virar processamento (ex.: extensão fora da lista aceita) — falha de negócio, não retry. */
public class UnsupportedVideoInputException extends RuntimeException {

  public UnsupportedVideoInputException(String message) {
    super(message);
  }
}
