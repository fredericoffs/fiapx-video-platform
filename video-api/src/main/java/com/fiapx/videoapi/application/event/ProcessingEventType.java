package com.fiapx.videoapi.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ProcessingEventType {

  @JsonProperty("ProcessingCompleted")
  PROCESSING_COMPLETED,

  @JsonProperty("ProcessingFailed")
  PROCESSING_FAILED
}
