package com.fiapx.videoapi.application.dto;

import java.io.InputStream;

public record VideoUploadCommand(
    String originalFilename,
    InputStream content,
    long contentLength,
    String contentType
) {

}
