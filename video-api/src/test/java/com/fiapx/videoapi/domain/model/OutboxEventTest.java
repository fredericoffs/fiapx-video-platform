package com.fiapx.videoapi.domain.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

  @Test
  void newEventStartsUnpublished() {
    UUID aggregateId = UUID.randomUUID();

    OutboxEvent event = OutboxEvent.newEvent(aggregateId, "VideoUploadRequested", "{}", "corr-id");

    assertThat(event.getAggregateId()).isEqualTo(aggregateId);
    assertThat(event.getEventType()).isEqualTo("VideoUploadRequested");
    assertThat(event.getCorrelationId()).isEqualTo("corr-id");
    assertThat(event.isPublished()).isFalse();
  }

  @Test
  void newEventAcceptsANullCorrelationId() {
    OutboxEvent event = OutboxEvent.newEvent(UUID.randomUUID(), "VideoUploadRequested", "{}", null);

    assertThat(event.getCorrelationId()).isNull();
  }

  @Test
  void markPublishedFlipsFlag() {
    OutboxEvent event = OutboxEvent.newEvent(UUID.randomUUID(), "VideoUploadRequested", "{}", "corr-id");

    event.markPublished();

    assertThat(event.isPublished()).isTrue();
  }

  @Test
  void hasNoLockTokenUntilAssigned() {
    OutboxEvent event = OutboxEvent.newEvent(UUID.randomUUID(), "VideoUploadRequested", "{}", "corr-id");

    assertThat(event.getLockToken()).isNull();

    UUID lockToken = UUID.randomUUID();
    event.assignLock(lockToken);

    assertThat(event.getLockToken()).isEqualTo(lockToken);
  }
}
