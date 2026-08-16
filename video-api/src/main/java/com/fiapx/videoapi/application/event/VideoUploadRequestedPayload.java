package com.fiapx.videoapi.application.event;

import java.util.UUID;

public record VideoUploadRequestedPayload(
    UUID videoId,
    String storageKey,
    String originalFilename
) {

}
