package com.fiapx.videoapi.application.dto;

public record LoginCommand(
    String email,
    String rawPassword
) {

}
