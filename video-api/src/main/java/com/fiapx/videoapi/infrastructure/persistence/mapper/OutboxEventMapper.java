package com.fiapx.videoapi.infrastructure.persistence.mapper;

import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import java.util.UUID;

public final class OutboxEventMapper {

  private OutboxEventMapper() {
  }

  public static OutboxEventEntity toEntity(OutboxEvent event) {
    OutboxEventEntity entity = new OutboxEventEntity();
    entity.setId(event.getId());
    entity.setAggregateId(event.getAggregateId());
    entity.setEventType(event.getEventType());
    entity.setPayload(event.getPayload());
    entity.setCorrelationId(event.getCorrelationId());
    entity.setPublished(event.isPublished());
    entity.setCreatedAt(event.getCreatedAt());
    entity.setAttempts(event.getAttempts());
    return entity;
  }

  /** lockToken é o token gerado nesta reivindicação, não o entity.getLockedBy() pré-lease. */
  public static OutboxEvent toDomain(OutboxEventEntity entity, UUID lockToken) {
    OutboxEvent event = new OutboxEvent(
        entity.getId(),
        entity.getAggregateId(),
        entity.getEventType(),
        entity.getPayload(),
        entity.getCorrelationId(),
        entity.isPublished(),
        entity.getCreatedAt(),
        entity.getAttempts()
    );
    event.assignLock(lockToken);
    return event;
  }
}
