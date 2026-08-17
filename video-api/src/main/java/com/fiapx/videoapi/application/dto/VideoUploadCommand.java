package com.fiapx.videoapi.application.dto;

import java.io.InputStream;
import java.util.UUID;

public record VideoUploadCommand(
    UUID userId,
    String originalFilename,
    InputStream content,
    long contentLength,
    String contentType
) {

}
