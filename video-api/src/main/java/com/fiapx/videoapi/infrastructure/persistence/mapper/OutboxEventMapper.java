package com.fiapx.videoapi.infrastructure.persistence.mapper;

import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;

public final class OutboxEventMapper {

  private OutboxEventMapper() {
  }

  public static OutboxEventEntity toEntity(OutboxEvent event) {
    OutboxEventEntity entity = new OutboxEventEntity();
    entity.setId(event.getId());
    entity.setAggregateId(event.getAggregateId());
    entity.setEventType(event.getEventType());
    entity.setPayload(event.getPayload());
    entity.setPublished(event.isPublished());
    entity.setCreatedAt(event.getCreatedAt());
    return entity;
  }

  public static OutboxEvent toDomain(OutboxEventEntity entity) {
    return new OutboxEvent(
        entity.getId(),
        entity.getAggregateId(),
        entity.getEventType(),
        entity.getPayload(),
        entity.isPublished(),
        entity.getCreatedAt()
    );
  }
}
