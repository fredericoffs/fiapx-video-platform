package com.fiapx.videoapi.domain.port;

import java.util.List;
import java.util.UUID;

import com.fiapx.videoapi.domain.model.OutboxEvent;

public interface OutboxEventRepository {

	void save(OutboxEvent event);

	List<OutboxEvent> findUnpublished(int limit);

	void markPublished(UUID eventId);
}
