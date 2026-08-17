package com.fiapx.videoapi.infrastructure.web.dto;

import com.fiapx.videoapi.application.dto.RegisterUserResult;
import java.util.UUID;

public record RegisterResponse(
    UUID id,
    String email
) {

  public static RegisterResponse from(RegisterUserResult result) {
    return new RegisterResponse(result.id(), result.email());
  }
}
