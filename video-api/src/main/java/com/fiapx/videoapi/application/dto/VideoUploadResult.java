package com.fiapx.videoapi.application.dto;

import com.fiapx.videoapi.domain.model.VideoStatus;
import java.util.UUID;

public record VideoUploadResult(
    UUID id,
    VideoStatus status
) {

}
