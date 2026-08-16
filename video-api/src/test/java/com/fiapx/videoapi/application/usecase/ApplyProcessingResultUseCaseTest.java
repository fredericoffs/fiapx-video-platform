package com.fiapx.videoapi.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fiapx.videoapi.application.event.ProcessingEventType;
import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.VideoRepository;

class ApplyProcessingResultUseCaseTest {

	private final VideoRepository videoRepository = mock(VideoRepository.class);
	private final ApplyProcessingResultUseCase useCase = new ApplyProcessingResultUseCase(videoRepository);

	@Test
	void ignoresEventForUnknownVideo() {
		UUID videoId = UUID.randomUUID();
		when(videoRepository.findById(videoId)).thenReturn(Optional.empty());

		useCase.handle(new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED, videoId, "zip-key", null));

		verify(videoRepository, never()).save(any());
	}

	@Test
	void ignoresEventForVideoAlreadyInTerminalStatus() {
		Video video = Video.newQueued(UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
		video.complete("already-processed.zip");
		when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

		useCase.handle(new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, video.getId(), null, "erro"));

		verify(videoRepository, never()).save(any());
	}

	@Test
	void appliesCompletedStatusFromProcessingCompletedEvent() {
		Video video = Video.newQueued(UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
		when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

		useCase.handle(
				new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED, video.getId(), "zip-key", null));

		ArgumentCaptor<Video> captor = ArgumentCaptor.forClass(Video.class);
		verify(videoRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(VideoStatus.COMPLETED);
		assertThat(captor.getValue().getZipStorageKey()).isEqualTo("zip-key");
	}

	@Test
	void appliesFailedStatusFromProcessingFailedEvent() {
		Video video = Video.newQueued(UUID.randomUUID(), "movie.mp4", "raw/movie.mp4");
		when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));

		useCase.handle(
				new ProcessingResultMessage(ProcessingEventType.PROCESSING_FAILED, video.getId(), null, "ffmpeg falhou"));

		ArgumentCaptor<Video> captor = ArgumentCaptor.forClass(Video.class);
		verify(videoRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(VideoStatus.FAILED);
		assertThat(captor.getValue().getErrorMessage()).isEqualTo("ffmpeg falhou");
	}
}
