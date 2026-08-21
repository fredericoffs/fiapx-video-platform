package com.fiapx.videoapi.application.event;

import java.util.UUID;

public record NotificationRequestedPayload(
    UUID videoId,
    String errorMessage,
    String recipientEmail
) {

}
