package com.fiapx.videoworker.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.domain.exception.FfmpegProcessingException;
import com.fiapx.videoworker.domain.model.ProcessingResult;
import com.fiapx.videoworker.domain.port.Archiver;
import com.fiapx.videoworker.domain.port.FrameExtractor;
import com.fiapx.videoworker.domain.port.StorageClient;
import com.fiapx.videoworker.infrastructure.config.FfmpegProperties;
import com.fiapx.videoworker.infrastructure.config.StorageProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProcessVideoUseCaseTest {

  private final StorageClient storageClient = mock(StorageClient.class);
  private final FrameExtractor frameExtractor = mock(FrameExtractor.class);
  private final Archiver archiver = mock(Archiver.class);
  private final StorageProperties storageProperties = new StorageProperties("http://localhost:9000", "key", "secret", "videos-raw",
      "videos-processed");
  private final FfmpegProperties ffmpegProperties = new FfmpegProperties("ffmpeg", 1, 15);
  private final ProcessVideoUseCase useCase = new ProcessVideoUseCase(storageClient, frameExtractor, archiver, storageProperties, ffmpegProperties);

  @Test
  void processesVideoSuccessfullyAndCleansUpTempDir() throws Exception {
    UUID videoId = UUID.randomUUID();
    VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/movie.mp4", "movie.mp4");

    when(storageClient.download(eq("videos-raw"), eq(payload.storageKey())))
        .thenReturn(new ByteArrayInputStream("fake-video-bytes".getBytes()));

    AtomicReference<Path> capturedTempDir = new AtomicReference<>();
    doAnswer(invocation -> {
          Path framesDir = invocation.getArgument(1);
          capturedTempDir.set(framesDir.getParent());
          Files.createFile(framesDir.resolve("frame_0001.png"));
          return null;
        }
    ).when(frameExtractor).extractFrames(any(), any(), anyInt());

    doAnswer(invocation -> {
          Path outputZip = invocation.getArgument(1);
          Files.write(outputZip, "fake-zip-bytes".getBytes());
          return null;
        }
    ).when(archiver).zip(any(), any());

    ProcessingResult result = useCase.handle(payload);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getVideoId()).isEqualTo(videoId);
    assertThat(result.getZipStorageKey()).isEqualTo("processed/" + videoId + "/" + videoId + ".zip");

    verify(storageClient).upload(eq("videos-processed"), eq(result.getZipStorageKey()), any(), anyLong(), eq("application/zip"));

    assertThat(capturedTempDir.get()).isNotNull();
    assertThat(Files.exists(capturedTempDir.get())).isFalse();
  }

  @Test
  void writesInputUnderAnInternalNameEvenWhenOriginalFilenameTriesPathTraversal() throws Exception {
    UUID videoId = UUID.randomUUID();
    Path outside = Files.createTempDirectory("fiapx-outside");
    String adversarial = "../../" + outside.getFileName() + "/pwned.mp4";
    VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/source.mp4",
        adversarial);

    when(storageClient.download(eq("videos-raw"), eq(payload.storageKey())))
        .thenReturn(new ByteArrayInputStream("fake-video-bytes".getBytes()));

    AtomicReference<Path> capturedVideoFile = new AtomicReference<>();
    doAnswer(invocation -> {
          Path videoFile = invocation.getArgument(0);
          Path framesDir = invocation.getArgument(1);
          capturedVideoFile.set(videoFile);
          Files.createFile(framesDir.resolve("frame_0001.png"));
          return null;
        }
    ).when(frameExtractor).extractFrames(any(), any(), anyInt());
    doAnswer(invocation -> {
          Files.write(invocation.getArgument(1), "zip".getBytes());
          return null;
        }
    ).when(archiver).zip(any(), any());

    ProcessingResult result = useCase.handle(payload);

    assertThat(result.isSuccess()).isTrue();
    assertThat(capturedVideoFile.get().getFileName().toString()).isEqualTo("input.mp4");
    assertThat(capturedVideoFile.get().getParent().getFileName().toString()).startsWith("fiapx-" + videoId);
    try (var leftovers = Files.list(outside)) {
      assertThat(leftovers).isEmpty();
    }
  }

  @Test
  void returnsFailureWhenNeitherStorageKeyNorOriginalFilenameHasSupportedExtension() {
    UUID videoId = UUID.randomUUID();
    VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/source.exe",
        "../../etc/passwd");

    ProcessingResult result = useCase.handle(payload);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorMessage()).contains("Extensão de vídeo não suportada");
    verify(storageClient, never()).download(anyString(), anyString());
  }

  @Test
  void fallsBackToOriginalFilenameExtensionWhenStorageKeyHasNone() throws Exception {
    UUID videoId = UUID.randomUUID();
    VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/source", "clip.WEBM");

    when(storageClient.download(anyString(), anyString()))
        .thenReturn(new ByteArrayInputStream("fake-video-bytes".getBytes()));
    AtomicReference<Path> capturedVideoFile = new AtomicReference<>();
    doAnswer(invocation -> {
          capturedVideoFile.set(invocation.getArgument(0));
          Files.createFile(((Path) invocation.getArgument(1)).resolve("frame_0001.png"));
          return null;
        }
    ).when(frameExtractor).extractFrames(any(), any(), anyInt());
    doAnswer(invocation -> {
          Files.write(invocation.getArgument(1), "zip".getBytes());
          return null;
        }
    ).when(archiver).zip(any(), any());

    ProcessingResult result = useCase.handle(payload);

    assertThat(result.isSuccess()).isTrue();
    assertThat(capturedVideoFile.get().getFileName().toString()).isEqualTo("input.webm");
  }

  @Test
  void returnsFailureWhenOriginalFilenameIsNullAndStorageKeyHasNoExtension() {
    UUID videoId = UUID.randomUUID();
    VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/source.", null);

    ProcessingResult result = useCase.handle(payload);

    assertThat(result.isSuccess()).isFalse();
    verify(storageClient, never()).download(anyString(), anyString());
  }

  @Test
  void returnsFailureResultWhenFfmpegBusinessFailureHappens() {
    UUID videoId = UUID.randomUUID();
    VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/movie.mp4", "movie.mp4");

    when(storageClient.download(anyString(), anyString()))
        .thenReturn(new ByteArrayInputStream("fake-video-bytes".getBytes()));
    doAnswer(invocation -> {
          throw new FfmpegProcessingException("ffmpeg saiu com código 1");
        }
    ).when(frameExtractor).extractFrames(any(), any(), anyInt());

    ProcessingResult result = useCase.handle(payload);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getVideoId()).isEqualTo(videoId);
    assertThat(result.getErrorMessage()).isEqualTo("ffmpeg saiu com código 1");
    verify(storageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
  }

  @Test
  void wrapsIOExceptionFromStorageDownloadAsUncheckedIOException() {
    UUID videoId = UUID.randomUUID();
    VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, "raw/" + videoId + "/movie.mp4", "movie.mp4");

    InputStream brokenStream = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("falha simulada de leitura");
      }
    };
    when(storageClient.download(anyString(), anyString())).thenReturn(brokenStream);

    assertThatThrownBy(() -> useCase.handle(payload))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining(videoId.toString());

    verify(frameExtractor, never()).extractFrames(any(), any(), anyInt());
  }
}
