package com.fiapx.videoworker.domain.exception;

public class FfmpegProcessingException extends RuntimeException {

	public FfmpegProcessingException(String message) {
		super(message);
	}

	public FfmpegProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
