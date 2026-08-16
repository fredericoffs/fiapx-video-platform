package com.fiapx.videoapi.domain.exception;

import java.util.UUID;

public class VideoNotFoundException extends RuntimeException {

	public VideoNotFoundException(UUID videoId) {
		super("Vídeo não encontrado: " + videoId);
	}
}
