package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplyProcessingResultUseCase {

  private static final Logger log = LoggerFactory.getLogger(ApplyProcessingResultUseCase.class);

  private final VideoRepository videoRepository;

  public ApplyProcessingResultUseCase(VideoRepository videoRepository) {
    this.videoRepository = videoRepository;
  }

  @Transactional
  public void handle(ProcessingResultMessage message) {
    Optional<Video> maybeVideo = videoRepository.findById(message.videoId());
    if (maybeVideo.isEmpty()) {
      log.warn("Evento de resultado recebido para vídeo inexistente: {}", message.videoId());
      return;
    }

    Video video = maybeVideo.get();
    if (video.isTerminal()) {
      log.info("Ignorando evento de resultado para vídeo {} já em estado terminal ({})", video.getId(), video.getStatus());
      return;
    }

    switch (message.eventType()) {
      case PROCESSING_COMPLETED -> video.complete(message.zipStorageKey());
      case PROCESSING_FAILED -> video.fail(message.errorMessage());
    }

    videoRepository.save(video);
  }
}
