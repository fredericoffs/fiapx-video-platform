package com.fiapx.videoapi.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEventEntity {

	@Id
	private UUID id;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Column(name = "correlation_id")
	private String correlationId;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(nullable = false)
	private int attempts;
}
