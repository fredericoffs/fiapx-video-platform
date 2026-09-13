package com.fiapx.videoapi.application.dto;

import java.util.UUID;

public record ChangePasswordCommand(
    UUID userId,
    String currentPassword,
    String newPassword
) {

}
