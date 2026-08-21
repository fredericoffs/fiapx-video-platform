package com.fiapx.videoapi.domain.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(UUID userId) {
		super("Usuário não encontrado: " + userId);
	}
}
