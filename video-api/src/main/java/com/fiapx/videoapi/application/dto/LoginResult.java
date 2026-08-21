package com.fiapx.videoapi.application.dto;

import com.fiapx.videoapi.domain.model.Role;

public record LoginResult(
    String accessToken,
    long expiresInSeconds,
    Role role
) {

}
