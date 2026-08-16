package com.fiapx.videoworker.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.domain.exception.FfmpegProcessingException;
import com.fiapx.videoworker.domain.model.ProcessingResult;
import com.fiapx.videoworker.domain.port.Archiver;
import com.fiapx.videoworker.domain.port.FrameExtractor;
import com.fiapx.videoworker.domain.port.StorageClient;
import com.fiapx.videoworker.infrastructure.config.FfmpegProperties;
import com.fiapx.videoworker.infrastructure.config.StorageProperties;

class ProcessVideoUseCaseTest {

	private final StorageClient storageClient = mock(StorageClient.class);
	private final FrameExtractor frameExtractor = mock(FrameExtractor.class);
	private final Archiver archiver = mock(Archiver.class);
	private final StorageProperties storageProperties = new StorageProperties("http://localhost:9000", "key",
			"secret", "videos-raw", "videos-processed");
	private final FfmpegProperties ffmpegProperties = new FfmpegProperties("ffmpeg", 1, 15);
	private final ProcessVideoUseCase useCase = new ProcessVideoUseCase(storageClient, frameExtractor, archiver,
			storageProperties, ffmpegProperties);

	@Test
	void processesVideoSuccessfullyAndCleansUpTempDir() throws Exception {
		UUID videoId = UUID.randomUUID();
		VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/movie.mp4",
				"movie.mp4");

		when(storageClient.download(eq("videos-raw"), eq(payload.storageKey())))
				.thenReturn(new ByteArrayInputStream("fake-video-bytes".getBytes()));

		AtomicReference<Path> capturedTempDir = new AtomicReference<>();
		doAnswer(invocation -> {
			Path framesDir = invocation.getArgument(1);
			capturedTempDir.set(framesDir.getParent());
			Files.createFile(framesDir.resolve("frame_0001.png"));
			return null;
		}).when(frameExtractor).extractFrames(any(), any(), anyInt());

		doAnswer(invocation -> {
			Path outputZip = invocation.getArgument(1);
			Files.write(outputZip, "fake-zip-bytes".getBytes());
			return null;
		}).when(archiver).zip(any(), any());

		ProcessingResult result = useCase.handle(payload);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.getVideoId()).isEqualTo(videoId);
		assertThat(result.getZipStorageKey()).isEqualTo("processed/" + videoId + "/" + videoId + ".zip");

		verify(storageClient).upload(eq("videos-processed"), eq(result.getZipStorageKey()), any(), anyLong(),
				eq("application/zip"));

		assertThat(capturedTempDir.get()).isNotNull();
		assertThat(Files.exists(capturedTempDir.get())).isFalse();
	}

	@Test
	void returnsFailureResultWhenFfmpegBusinessFailureHappens() {
		UUID videoId = UUID.randomUUID();
		VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/movie.mp4",
				"movie.mp4");

		when(storageClient.download(anyString(), anyString()))
				.thenReturn(new ByteArrayInputStream("fake-video-bytes".getBytes()));
		doAnswer(invocation -> {
			throw new FfmpegProcessingException("ffmpeg saiu com código 1");
		}).when(frameExtractor).extractFrames(any(), any(), anyInt());

		ProcessingResult result = useCase.handle(payload);

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.getVideoId()).isEqualTo(videoId);
		assertThat(result.getErrorMessage()).isEqualTo("ffmpeg saiu com código 1");
		verify(storageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
	}
}
