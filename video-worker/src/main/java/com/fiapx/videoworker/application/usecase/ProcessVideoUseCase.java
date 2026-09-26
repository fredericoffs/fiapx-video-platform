package com.fiapx.videoworker.application.usecase;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.domain.exception.FfmpegProcessingException;
import com.fiapx.videoworker.domain.exception.UnsupportedVideoInputException;
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
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

@Service
public class ProcessVideoUseCase {

  private static final Logger log = LoggerFactory.getLogger(ProcessVideoUseCase.class);
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv", "webm");

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

  /**
   * Extensão só da lista aceita, vinda da chave de storage (gerada pela API) ou, como fallback, do nome original.
   */
  private static String inputExtension(VideoUploadRequestedPayload payload) {
    String fromKey = extensionOf(payload.storageKey());
    if (ALLOWED_EXTENSIONS.contains(fromKey)) {
      return fromKey;
    }
    String fromName = extensionOf(payload.originalFilename());
    if (ALLOWED_EXTENSIONS.contains(fromName)) {
      return fromName;
    }
    throw new UnsupportedVideoInputException(
        "Extensão de vídeo não suportada para " + payload.videoId() + " (" + payload.originalFilename() + ")");
  }

  private static String extensionOf(String name) {
    if (name == null) {
      return "";
    }
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return "";
    }
    String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
    return ext.chars().allMatch(Character::isLetterOrDigit) ? ext : "";
  }

  private static Path resolveInside(Path base, String fileName) {
    Path resolved = base.resolve(fileName).normalize();
    if (!resolved.startsWith(base) || resolved.equals(base)) {
      throw new IllegalStateException("Caminho fora do diretório temporário: " + resolved);
    }
    return resolved;
  }

  public ProcessingResult handle(VideoUploadRequestedPayload payload) {
    return handle(payload, () -> {
    });
  }

  public ProcessingResult handle(VideoUploadRequestedPayload payload, Runnable verifyLease) {
    String zipKey = "processed/" + payload.videoId() + "/" + payload.videoId() + ".zip";
    // Reentrega (redelivery do SQS após visibility timeout, ou reprocessamento manual): o
    // ffmpeg já rodou até o fim numa tentativa anterior e o zip já está no destino final. Sem
    // DB, a própria existência do objeto de saída é a evidência de "já processado" — evita
    // rodar o ffmpeg de novo à toa numa mensagem duplicada.
    if (storageClient.exists(storageProperties.bucketProcessed(), zipKey)) {
      log.info("Vídeo {} já processado (zip existente em {}), pulando reentrega", payload.videoId(), zipKey);
      return ProcessingResult.success(payload.videoId(), zipKey);
    }

    Path tempDir = createTempDir(payload);
    try {
      // Nome interno controlado: o nome original do usuário nunca vira caminho em disco.
      Path videoFile = resolveInside(tempDir, "input." + inputExtension(payload));
      try (InputStream in = storageClient.download(storageProperties.bucketRaw(), payload.storageKey())) {
        Files.copy(in, videoFile, StandardCopyOption.REPLACE_EXISTING);
      }

      Path framesDir = Files.createDirectory(tempDir.resolve("frames"));
      frameExtractor.extractFrames(videoFile, framesDir, ffmpegProperties.fps());

      Path zipFile = tempDir.resolve(payload.videoId() + ".zip");
      archiver.zip(framesDir, zipFile);

      verifyLease.run();
      try (InputStream zipIn = Files.newInputStream(zipFile)) {
        storageClient.upload(storageProperties.bucketProcessed(), zipKey, zipIn, Files.size(zipFile),
            "application/zip");
      }

      return ProcessingResult.success(payload.videoId(), zipKey);
    } catch (FfmpegProcessingException | UnsupportedVideoInputException businessFailure) {
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
