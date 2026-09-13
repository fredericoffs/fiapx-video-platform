package com.fiapx.videoapi.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void mapsMaxUploadSizeExceededTo413() {
    ProblemDetail problem = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(500L * 1024 * 1024));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    assertThat(problem.getTitle()).isEqualTo("Arquivo excede o tamanho máximo permitido");
  }
}
