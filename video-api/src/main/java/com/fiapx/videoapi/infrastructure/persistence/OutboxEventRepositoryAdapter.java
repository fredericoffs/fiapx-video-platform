package com.fiapx.videoapi.infrastructure.persistence;

import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.infrastructure.persistence.mapper.OutboxEventMapper;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

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

  @Override
  public List<OutboxEvent> findUnpublished(int limit) {
    return springDataOutboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, limit))
        .stream()
        .map(OutboxEventMapper::toDomain)
        .toList();
  }

  @Override
  public void markPublished(UUID eventId) {
    springDataOutboxEventRepository.findById(eventId)
        .ifPresent(entity -> {
          entity.setPublished(true);
          springDataOutboxEventRepository.save(entity);
        });
  }
}
