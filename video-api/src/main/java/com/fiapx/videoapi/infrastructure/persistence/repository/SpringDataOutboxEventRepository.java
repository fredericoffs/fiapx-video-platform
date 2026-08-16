package com.fiapx.videoapi.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

	List<OutboxEventEntity> findByPublishedFalseOrderByCreatedAtAsc(Pageable pageable);
}
