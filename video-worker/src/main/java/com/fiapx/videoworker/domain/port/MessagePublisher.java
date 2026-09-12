package com.fiapx.videoworker.domain.port;

/** Publicação confirmada: só retorna quando o broker aceitou a mensagem; caso contrário lança exceção. */
public interface MessagePublisher {

  void publish(String queueName, String payloadJson, String correlationId);
}
