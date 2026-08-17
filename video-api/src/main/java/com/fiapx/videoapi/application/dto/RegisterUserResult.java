package com.fiapx.videoapi.application.dto;

import java.util.UUID;

public record RegisterUserResult(
    UUID id,
    String email
) {

}
