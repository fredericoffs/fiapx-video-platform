package com.fiapx.videoapi.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class OutboxEventTest {

	@Test
	void newEventStartsUnpublished() {
		UUID aggregateId = UUID.randomUUID();

		OutboxEvent event = OutboxEvent.newEvent(aggregateId, "VideoUploadRequested", "{}");

		assertThat(event.getAggregateId()).isEqualTo(aggregateId);
		assertThat(event.getEventType()).isEqualTo("VideoUploadRequested");
		assertThat(event.isPublished()).isFalse();
	}

	@Test
	void markPublishedFlipsFlag() {
		OutboxEvent event = OutboxEvent.newEvent(UUID.randomUUID(), "VideoUploadRequested", "{}");

		event.markPublished();

		assertThat(event.isPublished()).isTrue();
	}
}
