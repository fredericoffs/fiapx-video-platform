package com.fiapx.videoapi.domain.service;

import com.fiapx.videoapi.domain.exception.UnsupportedVideoFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoFormatValidatorTest {

  @ParameterizedTest
  @ValueSource(strings = {"movie.mp4", "movie.mov", "movie.avi", "movie.mkv", "movie.webm", "MOVIE.MP4"})
  void acceptsSupportedExtensions(String filename) {
    assertThatCode(() -> VideoFormatValidator.validate(filename)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"movie.txt", "movie.exe", "movie.pdf", "movie", "movie.", "archive.tar.gz"})
  void rejectsUnsupportedExtensions(String filename) {
    assertThatThrownBy(() -> VideoFormatValidator.validate(filename))
        .isInstanceOf(UnsupportedVideoFormatException.class)
        .hasMessageContaining(filename);
  }

  @Test
  void rejectsNullFilename() {
    assertThatThrownBy(() -> VideoFormatValidator.validate(null))
        .isInstanceOf(UnsupportedVideoFormatException.class);
  }

  @Test
  void rejectsBlankFilename() {
    assertThatThrownBy(() -> VideoFormatValidator.validate(""))
        .isInstanceOf(UnsupportedVideoFormatException.class);
  }
}
