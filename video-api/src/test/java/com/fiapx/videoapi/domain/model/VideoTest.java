package com.fiapx.videoapi.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class VideoTest {

	@Test
	void newQueuedStartsInQueuedStatus() {
		Video video = Video.newQueued(UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");

		assertThat(video.getStatus()).isEqualTo(VideoStatus.QUEUED);
		assertThat(video.isTerminal()).isFalse();
		assertThat(video.getZipStorageKey()).isNull();
		assertThat(video.getErrorMessage()).isNull();
	}

	@Test
	void completeSetsCompletedStatusAndZipKey() {
		Video video = Video.newQueued(UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");

		video.complete("processed/movie.zip");

		assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
		assertThat(video.getZipStorageKey()).isEqualTo("processed/movie.zip");
		assertThat(video.isTerminal()).isTrue();
	}

	@Test
	void failSetsFailedStatusAndErrorMessage() {
		Video video = Video.newQueued(UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");

		video.fail("ffmpeg falhou");

		assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
		assertThat(video.getErrorMessage()).isEqualTo("ffmpeg falhou");
		assertThat(video.isTerminal()).isTrue();
	}
}
