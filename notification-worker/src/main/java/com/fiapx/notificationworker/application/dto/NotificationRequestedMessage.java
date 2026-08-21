package com.fiapx.notificationworker.application.dto;

import java.util.UUID;

public record NotificationRequestedMessage(
    UUID videoId,
    String errorMessage,
    String recipientEmail
) {

}
