package com.fiapx.videoworker.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FfmpegProcessingExceptionTest {

  @Test
  void carriesMessageOnly() {
    FfmpegProcessingException exception = new FfmpegProcessingException("ffmpeg saiu com código 1");

    assertThat(exception.getMessage()).isEqualTo("ffmpeg saiu com código 1");
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void carriesMessageAndCause() {
    RuntimeException cause = new RuntimeException("processo interrompido");

    FfmpegProcessingException exception = new FfmpegProcessingException("falha ao executar ffmpeg", cause);

    assertThat(exception.getMessage()).isEqualTo("falha ao executar ffmpeg");
    assertThat(exception.getCause()).isSameAs(cause);
  }
}
