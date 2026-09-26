package com.fiapx.notificationworker.infrastructure.persistence.repository;

import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.infrastructure.persistence.entity.NotificationAttemptEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationAttemptRepository extends JpaRepository<NotificationAttemptEntity, UUID> {

  boolean existsByVideoIdAndChannelAndStatus(UUID videoId, com.fiapx.notificationworker.domain.model.NotificationChannelType channel,
      NotificationStatus status);

  @Modifying
  @Query(value = "UPDATE notification_worker.notification_attempts SET status = 'FAILED', error_message = 'Tentativa abandonada; liberada para reentrega' WHERE video_id = :videoId AND channel = :channel AND status = 'SENDING' AND created_at < now() - interval '2 minutes'", nativeQuery = true)
  @org.springframework.transaction.annotation.Transactional
  int expireAbandoned(@Param("videoId") UUID videoId, @Param("channel") String channel);

  @Modifying
  @Query("UPDATE NotificationAttemptEntity e SET e.status = :status, e.errorMessage = :errorMessage WHERE e.id = :id AND e.status = com.fiapx.notificationworker.domain.model.NotificationStatus.SENDING")
  int updateStatus(@Param("id") UUID id, @Param("status") NotificationStatus status,
      @Param("errorMessage") String errorMessage);
}
