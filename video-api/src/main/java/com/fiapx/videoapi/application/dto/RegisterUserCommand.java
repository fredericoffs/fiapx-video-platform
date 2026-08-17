package com.fiapx.videoapi.application.dto;

public record RegisterUserCommand(
    String email,
    String rawPassword
) {

}
