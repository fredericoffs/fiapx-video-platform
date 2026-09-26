package com.fiapx.videoapi.infrastructure.persistence;

import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.mapper.OutboxEventMapper;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxEventRepositoryAdapter implements com.fiapx.videoapi.domain.port.OutboxEventRepository {

  private final SpringDataOutboxEventRepository springDataOutboxEventRepository;

  public OutboxEventRepositoryAdapter(SpringDataOutboxEventRepository springDataOutboxEventRepository) {
    this.springDataOutboxEventRepository = springDataOutboxEventRepository;
  }

  @Override
  public void save(OutboxEvent event) {
    springDataOutboxEventRepository.save(OutboxEventMapper.toEntity(event));
  }

  // Transação curta: só o SELECT ... FOR UPDATE SKIP LOCKED e o UPDATE do lease. A
  // publicação (rede) acontece fora, no job, sem segurar conexão de banco aberta.
  @Override
  @Transactional
  public List<OutboxEvent> claimUnpublished(int limit, Duration lease) {
    List<OutboxEventEntity> claimed = springDataOutboxEventRepository.lockUnpublished(limit);
    if (claimed.isEmpty()) {
      return List.of();
    }
    UUID lockToken = UUID.randomUUID();
    List<UUID> ids = claimed.stream().map(OutboxEventEntity::getId).toList();
    springDataOutboxEventRepository.lease(ids, Instant.now().plus(lease), lockToken);
    return claimed.stream().map(entity -> OutboxEventMapper.toDomain(entity, lockToken)).toList();
  }

  @Override
  @Transactional
  public void markPublished(UUID eventId, UUID lockToken) {
    springDataOutboxEventRepository.markPublished(eventId, lockToken);
  }

  @Override
  @Transactional
  public void releaseAfterFailure(UUID eventId, UUID lockToken) {
    springDataOutboxEventRepository.releaseAfterFailure(eventId, lockToken);
  }
}
