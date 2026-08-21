package com.fiapx.notificationworker.domain.port;

import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NotificationChannel {

  NotificationChannelType type();

  CompletableFuture<Void> send(UUID videoId, String errorMessage, String recipientEmail);
}
