package com.fiapx.videoapi.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

	// SKIP LOCKED: linhas já reservadas por outra transação (outra réplica) são puladas em vez
	// de bloquear; locked_until expirado torna a linha elegível de novo.
	@Query(value = """
			SELECT * FROM video_api.outbox_events
			WHERE published = FALSE AND (locked_until IS NULL OR locked_until < now())
			ORDER BY created_at ASC
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxEventEntity> lockUnpublished(@Param("limit") int limit);

	@Modifying
	@Query("UPDATE OutboxEventEntity e SET e.published = TRUE, e.lockedUntil = NULL WHERE e.id = :id")
	int markPublished(@Param("id") UUID id);

	@Modifying
	@Query("UPDATE OutboxEventEntity e SET e.lockedUntil = NULL, e.attempts = e.attempts + 1 WHERE e.id = :id")
	int releaseAfterFailure(@Param("id") UUID id);

	@Modifying
	@Query("UPDATE OutboxEventEntity e SET e.lockedUntil = :until WHERE e.id IN :ids")
	int lease(@Param("ids") List<UUID> ids, @Param("until") Instant until);
}
