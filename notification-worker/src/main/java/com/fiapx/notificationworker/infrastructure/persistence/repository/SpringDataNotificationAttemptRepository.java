package com.fiapx.notificationworker.infrastructure.persistence.repository;

import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.infrastructure.persistence.entity.NotificationAttemptEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationAttemptRepository extends JpaRepository<NotificationAttemptEntity, UUID> {

  @Modifying
  @Query("UPDATE NotificationAttemptEntity e SET e.status = :status, e.errorMessage = :errorMessage WHERE e.id = :id")
  int updateStatus(@Param("id") UUID id, @Param("status") NotificationStatus status,
      @Param("errorMessage") String errorMessage);
}
