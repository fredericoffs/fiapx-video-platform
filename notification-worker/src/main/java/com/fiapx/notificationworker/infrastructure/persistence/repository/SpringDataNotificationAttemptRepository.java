package com.fiapx.notificationworker.infrastructure.persistence.repository;

import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.infrastructure.persistence.entity.NotificationAttemptEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotificationAttemptRepository extends JpaRepository<NotificationAttemptEntity, UUID> {

  boolean existsByVideoIdAndChannelAndStatus(UUID videoId, NotificationChannelType channel, NotificationStatus status);
}
