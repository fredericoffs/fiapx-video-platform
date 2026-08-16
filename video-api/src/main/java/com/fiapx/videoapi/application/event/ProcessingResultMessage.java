package com.fiapx.videoapi.application.event;

import java.util.UUID;

public record ProcessingResultMessage(
    ProcessingEventType eventType,
    UUID videoId,
    String zipStorageKey,
    String errorMessage
) {

}
