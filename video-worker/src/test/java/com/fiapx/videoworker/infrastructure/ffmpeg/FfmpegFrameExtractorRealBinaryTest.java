package com.fiapx.videoworker.infrastructure.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiapx.videoworker.domain.exception.FfmpegProcessingException;
import com.fiapx.videoworker.infrastructure.config.FfmpegProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/** Roda contra o ffmpeg de verdade quando ele está no PATH (CI instala; local depende da máquina). */
@EnabledIf("ffmpegAvailable")
class FfmpegFrameExtractorRealBinaryTest {

  @TempDir
  Path tempDir;

  static boolean ffmpegAvailable() {
    try {
      Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
      p.getInputStream().readAllBytes();
      return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  @Test
  void extractsOneFramePerSecondFromGeneratedVideo() throws Exception {
    Path video = tempDir.resolve("input.mp4");
    Process gen = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi",
        "-i", "testsrc=duration=3:size=64x64:rate=5", video.toString()).redirectErrorStream(true).start();
    gen.getInputStream().readAllBytes();
    assertThat(gen.waitFor(60, TimeUnit.SECONDS) && gen.exitValue() == 0).isTrue();

    Path frames = Files.createDirectory(tempDir.resolve("frames"));
    // maxDurationSeconds=60: bem acima dos 3s do vídeo, prova que o ffprobe real não barra o caminho feliz.
    new FfmpegFrameExtractor(new FfmpegProperties("ffmpeg", "ffprobe", 1, 5, 60)).extractFrames(video, frames, 1);

    try (Stream<Path> files = Files.list(frames)) {
      assertThat(files.filter(p -> p.toString().endsWith(".png")).count()).isBetween(2L, 4L);
    }
  }

  @Test
  void reportsFfmpegErrorForCorruptedInput() throws IOException {
    Path video = Files.writeString(tempDir.resolve("broken.mp4"), "isto não é um vídeo");
    Path frames = Files.createDirectory(tempDir.resolve("frames"));

    // maxDurationSeconds=0: desliga o limite, senão o próprio ffprobe já rejeitaria a entrada
    // corrompida antes de chegar no ffmpeg, que é o que este teste quer exercitar.
    assertThatThrownBy(() -> new FfmpegFrameExtractor(new FfmpegProperties("ffmpeg", "ffprobe", 1, 5, 0))
        .extractFrames(video, frames, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("código");
  }

  @Test
  void rejectsVideoLongerThanConfiguredLimit() throws Exception {
    Path video = tempDir.resolve("input.mp4");
    Process gen = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi",
        "-i", "testsrc=duration=3:size=64x64:rate=5", video.toString()).redirectErrorStream(true).start();
    gen.getInputStream().readAllBytes();
    assertThat(gen.waitFor(60, TimeUnit.SECONDS) && gen.exitValue() == 0).isTrue();

    Path frames = Files.createDirectory(tempDir.resolve("frames"));

    assertThatThrownBy(() -> new FfmpegFrameExtractor(new FfmpegProperties("ffmpeg", "ffprobe", 1, 5, 1))
        .extractFrames(video, frames, 1))
        .isInstanceOf(FfmpegProcessingException.class)
        .hasMessageContaining("excede o limite de 1s");
    try (Stream<Path> files = Files.list(frames)) {
      assertThat(files).isEmpty();
    }
  }
}
