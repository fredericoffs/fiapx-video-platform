package com.fiapx.videoworker.application.dto;

import java.util.UUID;

public record VideoUploadRequestedPayload(
    UUID videoId,
    String storageKey,
    String originalFilename
) {

}
