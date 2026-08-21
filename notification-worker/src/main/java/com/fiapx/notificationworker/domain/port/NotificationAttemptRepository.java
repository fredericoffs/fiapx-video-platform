package com.fiapx.notificationworker.domain.port;

import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import java.util.UUID;

public interface NotificationAttemptRepository {

  void save(NotificationAttempt attempt);

  boolean existsSent(UUID videoId, NotificationChannelType channel);
}
