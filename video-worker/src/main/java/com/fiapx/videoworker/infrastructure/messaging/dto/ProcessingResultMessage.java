package com.fiapx.videoworker.infrastructure.messaging.dto;

import java.util.UUID;

public record ProcessingResultMessage(
    ProcessingEventType eventType,
    UUID videoId,
    String zipStorageKey,
    String errorMessage
) {

}
