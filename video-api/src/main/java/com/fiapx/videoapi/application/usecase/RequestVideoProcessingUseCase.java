package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.dto.VideoUploadCommand;
import com.fiapx.videoapi.application.dto.VideoUploadResult;
import com.fiapx.videoapi.application.event.VideoUploadRequestedPayload;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.domain.port.StorageClient;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RequestVideoProcessingUseCase {

  public static final String EVENT_TYPE_VIDEO_UPLOAD_REQUESTED = "VideoUploadRequested";

  private final VideoRepository videoRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final StorageClient storageClient;
  private final StorageProperties storageProperties;
  private final ObjectMapper objectMapper;

  public RequestVideoProcessingUseCase(
      VideoRepository videoRepository,
      OutboxEventRepository outboxEventRepository,
      StorageClient storageClient,
      StorageProperties storageProperties,
      ObjectMapper objectMapper
  ) {
    this.videoRepository = videoRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.storageClient = storageClient;
    this.storageProperties = storageProperties;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public VideoUploadResult handle(VideoUploadCommand command) {
    UUID videoId = UUID.randomUUID();
    String storageKey = "raw/" + videoId + "/" + command.originalFilename();

    storageClient.upload(
        storageProperties.bucketRaw(),
        storageKey,
        command.content(),
        command.contentLength(),
        command.contentType()
    );

    Video video = Video.newQueued(videoId, command.originalFilename(), storageKey);
    videoRepository.save(video);

    VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(
        videoId,
        storageKey,
        command.originalFilename()
    );
    OutboxEvent event = OutboxEvent.newEvent(videoId, EVENT_TYPE_VIDEO_UPLOAD_REQUESTED, writeJson(payload));
    outboxEventRepository.save(event);

    return new VideoUploadResult(video.getId(), video.getStatus());
  }

  private String writeJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JacksonException e) {
      throw new IllegalStateException("Falha ao serializar payload do evento", e);
    }
  }
}
