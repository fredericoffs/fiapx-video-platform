package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.exception.VideoBeingProcessedException;
import com.fiapx.videoapi.domain.exception.VideoNotFoundException;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.StorageCleanup;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeleteVideoUseCase {

  private final VideoRepository videoRepository;
  private final StorageCleanup storageClient;
  private final StorageProperties storageProperties;

  public DeleteVideoUseCase(
      VideoRepository videoRepository,
      StorageCleanup storageClient,
      StorageProperties storageProperties
  ) {
    this.videoRepository = videoRepository;
    this.storageClient = storageClient;
    this.storageProperties = storageProperties;
  }

  @org.springframework.transaction.annotation.Transactional
  public void handle(UUID videoId, UUID requesterId, boolean isAdmin) {
    Video video = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
    if (!video.belongsTo(requesterId) && !isAdmin) {
      throw new VideoNotFoundException(videoId);
    }
    // A limpeza de objetos é persistida na mesma transação da exclusão lógica do registro.
    // Estados ativos não podem ser excluídos; tentativas de storage são retomadas pelo job.
    if (!video.isTerminal()) {
      throw new VideoBeingProcessedException(videoId);
    }

    storageClient.delete(storageProperties.bucketRaw(), video.getStorageKey());
    if (video.getZipStorageKey() != null) {
      storageClient.delete(storageProperties.bucketProcessed(), video.getZipStorageKey());
    }
    videoRepository.deleteById(videoId);
  }
}
