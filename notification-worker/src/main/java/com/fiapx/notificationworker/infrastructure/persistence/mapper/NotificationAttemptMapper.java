package com.fiapx.notificationworker.infrastructure.persistence.mapper;

import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.infrastructure.persistence.entity.NotificationAttemptEntity;

public final class NotificationAttemptMapper {

  private NotificationAttemptMapper() {
  }

  public static NotificationAttemptEntity toEntity(NotificationAttempt attempt) {
    NotificationAttemptEntity entity = new NotificationAttemptEntity();
    entity.setId(attempt.getId());
    entity.setVideoId(attempt.getVideoId());
    entity.setChannel(attempt.getChannel());
    entity.setStatus(attempt.getStatus());
    entity.setErrorMessage(attempt.getErrorMessage());
    entity.setCreatedAt(attempt.getCreatedAt());
    return entity;
  }
}
