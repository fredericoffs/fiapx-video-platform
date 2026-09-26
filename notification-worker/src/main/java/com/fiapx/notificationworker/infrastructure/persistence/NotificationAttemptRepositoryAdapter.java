package com.fiapx.notificationworker.infrastructure.persistence;

import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.infrastructure.persistence.mapper.NotificationAttemptMapper;
import com.fiapx.notificationworker.infrastructure.persistence.repository.SpringDataNotificationAttemptRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationAttemptRepositoryAdapter implements NotificationAttemptRepository {

  private final SpringDataNotificationAttemptRepository springDataNotificationAttemptRepository;

  public NotificationAttemptRepositoryAdapter(
      SpringDataNotificationAttemptRepository springDataNotificationAttemptRepository
  ) {
    this.springDataNotificationAttemptRepository = springDataNotificationAttemptRepository;
  }

  // O índice único parcial (video_id, channel) WHERE status IN ('SENDING', 'SENT') é a garantia
  // real de idempotência: só uma execução consegue inserir essa linha por vez, mesmo com
  // reentrega da fila ou duas réplicas disputando a mesma mensagem.
  @Override
  public Optional<NotificationAttempt> tryClaim(UUID videoId, NotificationChannelType channel) {
    expireAbandoned(videoId, channel);
    NotificationAttempt attempt = NotificationAttempt.claiming(videoId, channel);
    try {
      springDataNotificationAttemptRepository.saveAndFlush(NotificationAttemptMapper.toEntity(attempt));
      return Optional.of(attempt);
    } catch (DataIntegrityViolationException e) {
      return Optional.empty();
    }
  }

  private void expireAbandoned(UUID videoId, NotificationChannelType channel) {
    springDataNotificationAttemptRepository.expireAbandoned(videoId, channel.name());
  }

  @Override
  public boolean isSent(UUID videoId, NotificationChannelType channel) {
    return springDataNotificationAttemptRepository.existsByVideoIdAndChannelAndStatus(videoId, channel, NotificationStatus.SENT);
  }

  @Override
  @Transactional
  public void markSent(UUID attemptId) {
    springDataNotificationAttemptRepository.updateStatus(attemptId, NotificationStatus.SENT, null);
  }

  @Override
  @Transactional
  public void markFailed(UUID attemptId, String errorMessage) {
    springDataNotificationAttemptRepository.updateStatus(attemptId, NotificationStatus.FAILED, errorMessage);
  }
}
