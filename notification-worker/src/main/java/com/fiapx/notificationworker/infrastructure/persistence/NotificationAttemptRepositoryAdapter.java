package com.fiapx.notificationworker.infrastructure.persistence;

import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.infrastructure.persistence.mapper.NotificationAttemptMapper;
import com.fiapx.notificationworker.infrastructure.persistence.repository.SpringDataNotificationAttemptRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationAttemptRepositoryAdapter implements NotificationAttemptRepository {

  private final SpringDataNotificationAttemptRepository springDataNotificationAttemptRepository;

  public NotificationAttemptRepositoryAdapter(
      SpringDataNotificationAttemptRepository springDataNotificationAttemptRepository
  ) {
    this.springDataNotificationAttemptRepository = springDataNotificationAttemptRepository;
  }

  @Override
  public void save(NotificationAttempt attempt) {
    springDataNotificationAttemptRepository.save(NotificationAttemptMapper.toEntity(attempt));
  }

  @Override
  public boolean existsSent(UUID videoId, NotificationChannelType channel) {
    return springDataNotificationAttemptRepository.existsByVideoIdAndChannelAndStatus(
        videoId, channel, NotificationStatus.SENT);
  }
}
