package com.fiapx.videoworker.infrastructure.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ProcessingEventType {

	@JsonProperty("ProcessingCompleted")
	PROCESSING_COMPLETED,

	@JsonProperty("ProcessingFailed")
	PROCESSING_FAILED
}
