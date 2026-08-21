package com.fiapx.videoapi.application.dto;

import com.fiapx.videoapi.domain.model.Video;

public record VideoWithOwner(
    Video video,
    String ownerEmail
) {

}
