package com.fiapx.videoapi.application.dto;

public record LoginResult(
    String accessToken,
    long expiresInSeconds
) {

}
