package com.fiapx.videoworker.domain.model;

/**
 * Mensagem a publicar: corpo JSON mais os metadados que viajam fora do corpo (header AMQP ou
 * atributo SQS) — correlation-id para rastreio e eventId para idempotência no consumidor.
 */
public record OutboundMessage(String payloadJson, String correlationId, String eventId) {

  public static OutboundMessage of(String payloadJson, String correlationId, String eventId) {
    return new OutboundMessage(payloadJson, correlationId, eventId);
  }
}
