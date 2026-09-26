package com.fiapx.videoapi.domain.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTest {

  @Test
  void newQueuedStartsInQueuedStatus() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");

    assertThat(video.getStatus()).isEqualTo(VideoStatus.QUEUED);
    assertThat(video.isTerminal()).isFalse();
    assertThat(video.getZipStorageKey()).isNull();
    assertThat(video.getErrorMessage()).isNull();
    assertThat(video.getFileSizeBytes()).as("sem tamanho quando não informado").isNull();
  }

  @Test
  void newQueuedStoresFileSizeBytesWhenProvided() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4", 10_485_760L);

    assertThat(video.getFileSizeBytes()).isEqualTo(10_485_760L);
  }

  @Test
  void startProcessingMovesQueuedToProcessingOnlyOnce() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");

    assertThat(video.startProcessing()).isTrue();
    assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSING);
    assertThat(video.isTerminal()).isFalse();

    assertThat(video.startProcessing()).as("started repetido é no-op").isFalse();
    assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSING);
  }

  @Test
  void lateStartProcessingNeverRegressesATerminalVideo() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
    video.complete("processed/movie.zip");

    assertThat(video.startProcessing()).isFalse();
    assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
  }

  @Test
  void completeSetsCompletedStatusAndZipKey() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");

    video.complete("processed/movie.zip");

    assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
    assertThat(video.getZipStorageKey()).isEqualTo("processed/movie.zip");
    assertThat(video.isTerminal()).isTrue();
  }

  @Test
  void failSetsFailedStatusAndErrorMessage() {
    Video video = Video.newQueued(UUID.randomUUID(), UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");

    video.fail("ffmpeg falhou");

    assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
    assertThat(video.getErrorMessage()).isEqualTo("ffmpeg falhou");
    assertThat(video.isTerminal()).isTrue();
  }
}
