package com.fiapx.videoworker.infrastructure.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiapx.videoworker.domain.exception.FfmpegProcessingException;
import com.fiapx.videoworker.infrastructure.config.FfmpegProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Usa um "ffmpeg" fake (script shell) para exercitar cada saída do adapter sem depender do
 * binário real: sucesso, código de erro, nenhum frame, timeout e binário inexistente.
 */
class FfmpegFrameExtractorTest {

  @TempDir
  Path tempDir;

  private Path videoFile;
  private Path framesDir;

  @BeforeEach
  void setUp() throws IOException {
    videoFile = Files.writeString(tempDir.resolve("input.mp4"), "fake");
    framesDir = Files.createDirectory(tempDir.resolve("frames"));
  }

  @Test
  void extractsFramesWhenBinarySucceeds() throws IOException {
    Path fake = fakeBinary("""
        #!/bin/sh
        for a in "$@"; do last="$a"; done
        touch "$(dirname "$last")/frame_0001.png"
        exit 0
        """);

    assertThatCode(() -> extractor(fake, 1).extractFrames(videoFile, framesDir, 1)).doesNotThrowAnyException();

    try (Stream<Path> files = Files.list(framesDir)) {
      assertThat(files).hasSize(1);
    }
    try (Stream<Path> leftovers = Files.list(tempDir)) {
      assertThat(leftovers.filter(p -> p.getFileName().toString().startsWith("ffmpeg-"))).isEmpty();
    }
  }

  @Test
  void failsWithExitCodeAndOutputWhenBinaryReturnsError() throws IOException {
    Path fake = fakeBinary("""
        #!/bin/sh
        echo "Invalid data found when processing input"
        exit 1
        """);

    assertThatThrownBy(() -> extractor(fake, 1).extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("código 1")
        .hasMessageContaining("Invalid data found");
  }

  @Test
  void failsWhenNoFrameIsProduced() throws IOException {
    Path fake = fakeBinary("""
        #!/bin/sh
        exit 0
        """);

    assertThatThrownBy(() -> extractor(fake, 1).extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("Nenhum frame");
  }

  @Test
  void killsProcessAndFailsWhenTimeoutExpires() throws IOException {
    Path fake = fakeBinary("""
        #!/bin/sh
        sleep 30
        exit 0
        """);

    Instant start = Instant.now();
    assertThatThrownBy(() -> extractor(fake, 0).extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("Timeout");

    // 0 minutos de prazo: o waitFor volta na hora e o processo (sleep 30) é derrubado, não esperado.
    assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(10));
  }

  @Test
  void killsProcessAndRestoresInterruptFlagWhenThreadIsInterrupted() throws IOException {
    Path fake = fakeBinary("""
        #!/bin/sh
        sleep 30
        exit 0
        """);

    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> extractor(fake, 5).extractFrames(videoFile, framesDir, 1))
          .isInstanceOf(FfmpegProcessingException.class)
          .hasMessageContaining("interrompida");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted(); // limpa a flag para não vazar para outros testes
    }
  }

  @Test
  void failsWhenFramesDirectoryCannotBeListed() throws IOException {
    Path fake = fakeBinary("""
        #!/bin/sh
        exit 0
        """);
    Path missingFramesDir = tempDir.resolve("missing-frames");

    assertThatThrownBy(() -> extractor(fake, 1).extractFrames(videoFile, missingFramesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("Falha ao ler diretório de frames");
  }

  @Test
  void rejectsVideoLongerThanConfiguredLimitWithoutRunningFfmpeg() throws IOException {
    Path fakeFfprobe = fakeBinary("""
        #!/bin/sh
        echo "125.3"
        exit 0
        """);
    // não deve nem ser invocado — se for, o teste falha pelo diretório de frames não vazio.
    Path fakeFfmpeg = fakeBinary("""
        #!/bin/sh
        for a in "$@"; do last="$a"; done
        touch "$(dirname "$last")/frame_0001.png"
        exit 0
        """);

    assertThatThrownBy(() -> extractorWithDurationLimit(fakeFfmpeg, fakeFfprobe, 60)
        .extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("125s")
        .hasMessageContaining("limite de 60s");
    try (Stream<Path> files = Files.list(framesDir)) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void allowsVideoWithinConfiguredDurationLimit() throws IOException {
    Path fakeFfprobe = fakeBinary("""
        #!/bin/sh
        echo "5.0"
        exit 0
        """);
    Path fakeFfmpeg = fakeBinary("""
        #!/bin/sh
        for a in "$@"; do last="$a"; done
        touch "$(dirname "$last")/frame_0001.png"
        exit 0
        """);

    assertThatCode(() -> extractorWithDurationLimit(fakeFfmpeg, fakeFfprobe, 60)
        .extractFrames(videoFile, framesDir, 1)).doesNotThrowAnyException();
  }

  @Test
  void failsWhenFfprobeReturnsNonZeroExitCode() throws IOException {
    Path fakeFfprobe = fakeBinary("""
        #!/bin/sh
        echo "moov atom not found"
        exit 1
        """);

    assertThatThrownBy(() -> extractorWithDurationLimit(tempDir.resolve("unused-ffmpeg"), fakeFfprobe, 60)
        .extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("ffprobe saiu com código 1")
        .hasMessageContaining("moov atom not found");
  }

  @Test
  void failsWhenFfprobeOutputIsNotANumber() throws IOException {
    Path fakeFfprobe = fakeBinary("""
        #!/bin/sh
        echo "N/A"
        exit 0
        """);

    assertThatThrownBy(() -> extractorWithDurationLimit(tempDir.resolve("unused-ffmpeg"), fakeFfprobe, 60)
        .extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("Não foi possível determinar a duração");
  }

  @Test
  void failsWhenFfprobeBinaryDoesNotExist() {
    Path missingFfprobe = tempDir.resolve("no-such-ffprobe");

    assertThatThrownBy(() -> extractorWithDurationLimit(tempDir.resolve("unused-ffmpeg"), missingFfprobe, 60)
        .extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("Falha ao executar ffprobe");
  }

  @Test
  void failsWhenBinaryDoesNotExist() {
    Path missing = tempDir.resolve("no-such-ffmpeg");

    assertThatThrownBy(() -> extractor(missing, 1).extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("Falha ao executar ffmpeg");
  }

  @Test
  void truncatesVeryLongOutputInErrorMessage() throws IOException {
    Path fake = fakeBinary("""
        #!/bin/sh
        i=0
        while [ $i -lt 3000 ]; do echo "linha de erro bem comprida numero $i com bastante texto repetido"; i=$((i+1)); done
        exit 2
        """);

    assertThatThrownBy(() -> extractor(fake, 1).extractFrames(videoFile, framesDir, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("[log truncado]")
        .satisfies(e -> assertThat(e.getMessage().length()).isLessThan(FfmpegFrameExtractor.MAX_LOG_BYTES + 200));
  }

  private FfmpegFrameExtractor extractor(Path binary, int timeoutMinutes) {
    // maxDurationSeconds=0 desliga o limite — estes testes não exercitam o ffprobe.
    return new FfmpegFrameExtractor(new FfmpegProperties(binary.toString(), "ffprobe", 1, timeoutMinutes, 0));
  }

  private FfmpegFrameExtractor extractorWithDurationLimit(Path ffmpeg, Path ffprobe, int maxDurationSeconds) {
    return new FfmpegFrameExtractor(
        new FfmpegProperties(ffmpeg.toString(), ffprobe.toString(), 1, 1, maxDurationSeconds));
  }

  private Path fakeBinary(String script) throws IOException {
    Path file = Files.writeString(tempDir.resolve("fake-ffmpeg-" + System.nanoTime() + ".sh"), script);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    return file;
  }
}
