package com.fiapx.videoapi.domain.model;

import java.time.Instant;
import java.util.UUID;

public class OutboxEvent {

  private final UUID id;
  private final UUID aggregateId;
  private final String eventType;
  private final String payload;
  private boolean published;
  private final Instant createdAt;

  public OutboxEvent(
      UUID id,
      UUID aggregateId,
      String eventType,
      String payload,
      boolean published,
      Instant createdAt
  ) {
    this.id = id;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.published = published;
    this.createdAt = createdAt;
  }

  public static OutboxEvent newEvent(
      UUID aggregateId,
      String eventType,
      String payload
  ) {
    return new OutboxEvent(UUID.randomUUID(), aggregateId, eventType, payload, false, Instant.now());
  }

  public void markPublished() {
    this.published = true;
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

  public boolean isPublished() {
    return published;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
