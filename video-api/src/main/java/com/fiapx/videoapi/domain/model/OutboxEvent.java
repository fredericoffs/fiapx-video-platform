package com.fiapx.videoapi.domain.model;

import java.time.Instant;
import java.util.UUID;

public class OutboxEvent {

  private final UUID id;
  private final UUID aggregateId;
  private final String eventType;
  private final String payload;
  private final String correlationId;
  private boolean published;
  private final Instant createdAt;
  private final int attempts;
  private UUID lockToken;

  public OutboxEvent(
      UUID id,
      UUID aggregateId,
      String eventType,
      String payload,
      String correlationId,
      boolean published,
      Instant createdAt
  ) {
    this(id, aggregateId, eventType, payload, correlationId, published, createdAt, 0);
  }

  public OutboxEvent(
      UUID id,
      UUID aggregateId,
      String eventType,
      String payload,
      String correlationId,
      boolean published,
      Instant createdAt,
      int attempts
  ) {
    this.id = id;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.correlationId = correlationId;
    this.published = published;
    this.createdAt = createdAt;
    this.attempts = attempts;
  }

  public static OutboxEvent newEvent(
      UUID aggregateId,
      String eventType,
      String payload,
      String correlationId
  ) {
    return newEvent(UUID.randomUUID(), aggregateId, eventType, payload, correlationId);
  }

  /** O id do evento é decidido por quem monta o payload, para viajar dentro dele (idempotência no consumidor). */
  public static OutboxEvent newEvent(
      UUID eventId,
      UUID aggregateId,
      String eventType,
      String payload,
      String correlationId
  ) {
    return new OutboxEvent(eventId, aggregateId, eventType, payload, correlationId, false, Instant.now(), 0);
  }

  public void markPublished() {
    this.published = true;
  }

  /** Token gerado pela reivindicação (lease) que trouxe este evento; identifica quem detém a reserva atual. */
  public void assignLock(UUID lockToken) {
    this.lockToken = lockToken;
  }

  public UUID getLockToken() {
    return lockToken;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public boolean isPublished() {
    return published;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public int getAttempts() {
    return attempts;
  }
}
