package com.fiapx.notificationworker.infrastructure.persistence.entity;

import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_attempts")
@Getter
@Setter
@NoArgsConstructor
public class NotificationAttemptEntity {

  @Id
  private UUID id;

  @Column(name = "video_id", nullable = false)
  private UUID videoId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationChannelType channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationStatus status;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
