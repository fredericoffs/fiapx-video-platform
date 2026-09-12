package com.fiapx.notificationworker.infrastructure.persistence;

import com.fiapx.notificationworker.domain.exception.DuplicateNotificationException;
import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.infrastructure.persistence.mapper.NotificationAttemptMapper;
import com.fiapx.notificationworker.infrastructure.persistence.repository.SpringDataNotificationAttemptRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class NotificationAttemptRepositoryAdapter implements NotificationAttemptRepository {

  private final SpringDataNotificationAttemptRepository springDataNotificationAttemptRepository;

  public NotificationAttemptRepositoryAdapter(
      SpringDataNotificationAttemptRepository springDataNotificationAttemptRepository
  ) {
    this.springDataNotificationAttemptRepository = springDataNotificationAttemptRepository;
  }

  // O índice único parcial (video_id, channel) WHERE status = 'SENT' é a garantia real de
  // idempotência; a violação vira exceção de domínio para o caso de uso decidir.
  @Override
  public void save(NotificationAttempt attempt) {
    try {
      springDataNotificationAttemptRepository.saveAndFlush(NotificationAttemptMapper.toEntity(attempt));
    } catch (DataIntegrityViolationException e) {
      throw new DuplicateNotificationException(attempt.getVideoId(), attempt.getChannel());
    }
  }

  @Override
  public boolean existsSent(UUID videoId, NotificationChannelType channel) {
    return springDataNotificationAttemptRepository.existsByVideoIdAndChannelAndStatus(
        videoId, channel, NotificationStatus.SENT);
  }
}
