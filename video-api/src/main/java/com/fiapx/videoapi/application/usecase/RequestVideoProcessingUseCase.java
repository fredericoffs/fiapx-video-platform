package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.VideoUploadCommand;
import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.application.event.VideoUploadRequestedPayload;
import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.domain.port.StorageCleanup;
import com.fiapx.videoapi.domain.port.StorageClient;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.domain.service.VideoFormatValidator;
import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Upload em três fases, pra nenhuma transação (nem conexão do pool, nem trava do usuário)
 * ficar aberta durante o envio ao S3:
 * <ol>
 *   <li>transação curta: confere o usuário e agenda a remoção da chave (reserva de limpeza);</li>
 *   <li>envio ao S3, fora de transação;</li>
 *   <li>transação curta: trava o usuário (mesma proteção contra exclusão concorrente de antes),
 *       cancela a reserva e grava vídeo + outbox atomicamente.</li>
 * </ol>
 * Qualquer falha depois da fase 1 (S3, usuário excluído no meio, rollback da fase 3) deixa a
 * reserva de pé, e {@code StorageCleanup} apaga o objeto órfão quando ela vence.
 */
@Service
public class RequestVideoProcessingUseCase {

  public static final String EVENT_TYPE_VIDEO_UPLOAD_REQUESTED = "VideoUploadRequested";

  // Bem acima do envio de 500MB do pod ao S3: vencer antes da fase 3 faz o upload ser rejeitado.
  static final Duration ORPHAN_CLEANUP_DELAY = Duration.ofHours(1);

  private final VideoRepository videoRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final StorageClient storageClient;
  private final StorageProperties storageProperties;
  private final ObjectMapper objectMapper;
  private final UserRepository userRepository;
  private final StorageCleanup storageCleanup;
  private final TransactionTemplate transactionTemplate;

  public RequestVideoProcessingUseCase(
      VideoRepository videoRepository,
      OutboxEventRepository outboxEventRepository,
      StorageClient storageClient,
      StorageProperties storageProperties,
      ObjectMapper objectMapper,
      UserRepository userRepository,
      StorageCleanup storageCleanup,
      TransactionTemplate transactionTemplate
  ) {
    this.videoRepository = videoRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.storageClient = storageClient;
    this.storageProperties = storageProperties;
    this.objectMapper = objectMapper;
    this.userRepository = userRepository;
    this.storageCleanup = storageCleanup;
    this.transactionTemplate = transactionTemplate;
  }

  public VideoUploadResult handle(VideoUploadCommand command) {
    String extension = VideoFormatValidator.validatedExtension(command.originalFilename());
    UUID videoId = UUID.randomUUID();
    // Nome interno controlado: o nome original do usuário fica só como metadado do vídeo.
    String storageKey = "raw/" + videoId + "/source." + extension;
    String bucket = storageProperties.bucketRaw();

    transactionTemplate.executeWithoutResult(status -> {
      userRepository.findById(command.userId())
          .orElseThrow(() -> new UserNotFoundException(command.userId()));
      storageCleanup.schedule(bucket, storageKey, ORPHAN_CLEANUP_DELAY);
    });

    storageClient.upload(bucket, storageKey, command.content(), command.contentLength(), command.contentType());

    return transactionTemplate.execute(status -> {
      userRepository.findByIdForUpdate(command.userId())
          .orElseThrow(() -> new UserNotFoundException(command.userId()));
      if (!storageCleanup.cancel(bucket, storageKey)) {
        throw new IllegalStateException("Reserva de limpeza de " + storageKey
            + " venceu antes do fim do upload; o objeto pode já ter sido removido");
      }

      Video video = Video.newQueued(
          videoId, command.userId(), command.originalFilename(), storageKey, command.contentLength()
      );
      videoRepository.save(video);

      UUID eventId = UUID.randomUUID();
      VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(
          videoId,
          storageKey,
          command.originalFilename(),
          eventId,
          VideoUploadRequestedPayload.CURRENT_CONTRACT_VERSION
      );
      OutboxEvent event = OutboxEvent.newEvent(
          eventId, videoId, EVENT_TYPE_VIDEO_UPLOAD_REQUESTED, writeJson(payload), MDC.get("correlationId")
      );
      outboxEventRepository.save(event);

      return new VideoUploadResult(video.getId(), video.getStatus());
    });
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JacksonException e) {
      throw new IllegalStateException("Falha ao serializar payload do evento", e);
    }
  }
}
