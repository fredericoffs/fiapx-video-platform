package com.fiapx.videoworker.application.usecase;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.domain.exception.FfmpegProcessingException;
import com.fiapx.videoworker.domain.model.ProcessingResult;
import com.fiapx.videoworker.domain.port.Archiver;
import com.fiapx.videoworker.domain.port.FrameExtractor;
import com.fiapx.videoworker.domain.port.StorageClient;
import com.fiapx.videoworker.infrastructure.config.FfmpegProperties;
import com.fiapx.videoworker.infrastructure.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

@Service
public class ProcessVideoUseCase {

  private static final Logger log = LoggerFactory.getLogger(ProcessVideoUseCase.class);

  private final StorageClient storageClient;
  private final FrameExtractor frameExtractor;
  private final Archiver archiver;
  private final StorageProperties storageProperties;
  private final FfmpegProperties ffmpegProperties;

  public ProcessVideoUseCase(
      StorageClient storageClient,
      FrameExtractor frameExtractor,
      Archiver archiver,
      StorageProperties storageProperties,
      FfmpegProperties ffmpegProperties
  ) {
    this.storageClient = storageClient;
    this.frameExtractor = frameExtractor;
    this.archiver = archiver;
    this.storageProperties = storageProperties;
    this.ffmpegProperties = ffmpegProperties;
  }

  public ProcessingResult handle(VideoUploadRequestedPayload payload) {
    Path tempDir = createTempDir(payload);
    try {
      Path videoFile = tempDir.resolve(payload.originalFilename());
      try (InputStream in = storageClient.download(storageProperties.bucketRaw(), payload.storageKey())) {
        Files.copy(in, videoFile, StandardCopyOption.REPLACE_EXISTING);
      }

      Path framesDir = Files.createDirectory(tempDir.resolve("frames"));
      frameExtractor.extractFrames(videoFile, framesDir, ffmpegProperties.fps());

      Path zipFile = tempDir.resolve(payload.videoId() + ".zip");
      archiver.zip(framesDir, zipFile);

      String zipKey = "processed/" + payload.videoId() + "/" + payload.videoId() + ".zip";
      try (InputStream zipIn = Files.newInputStream(zipFile)) {
        storageClient.upload(storageProperties.bucketProcessed(), zipKey, zipIn, Files.size(zipFile),
            "application/zip");
      }

      return ProcessingResult.success(payload.videoId(), zipKey);
    } catch (FfmpegProcessingException businessFailure) {
      return ProcessingResult.failure(payload.videoId(), businessFailure.getMessage());
    } catch (IOException e) {
      throw new UncheckedIOException("Falha de I/O ao processar vídeo " + payload.videoId(), e);
    } finally {
      try {
        FileSystemUtils.deleteRecursively(tempDir);
      } catch (IOException e) {
        log.warn("Falha ao limpar diretório temporário {}", tempDir, e);
      }
    }
  }

  private Path createTempDir(VideoUploadRequestedPayload payload) {
    try {
      return Files.createTempDirectory("fiapx-" + payload.videoId());
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao criar diretório temporário para o vídeo " + payload.videoId(), e);
    }
  }
}
