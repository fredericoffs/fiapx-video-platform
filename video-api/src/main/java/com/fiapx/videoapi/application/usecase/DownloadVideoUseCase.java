package com.fiapx.videoapi.application.usecase;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fiapx.videoapi.application.dto.VideoDownload;
import com.fiapx.videoapi.domain.exception.VideoNotCompletedException;
import com.fiapx.videoapi.domain.exception.VideoNotFoundException;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.StorageClient;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.config.StorageProperties;

@Service
public class DownloadVideoUseCase {

  private final VideoRepository videoRepository;
  private final StorageClient storageClient;
  private final StorageProperties storageProperties;

  public DownloadVideoUseCase(
      VideoRepository videoRepository,
      StorageClient storageClient,
      StorageProperties storageProperties
  ) {
    this.videoRepository = videoRepository;
    this.storageClient = storageClient;
    this.storageProperties = storageProperties;
  }

  public VideoDownload handle(UUID videoId, UUID requesterId) {
    Video video = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
    if (!video.belongsTo(requesterId)) {
      throw new VideoNotFoundException(videoId);
    }
    if (video.getStatus() != VideoStatus.COMPLETED) {
      throw new VideoNotCompletedException(videoId);
    }

    InputStream content = storageClient.download(storageProperties.bucketProcessed(), video.getZipStorageKey());
    return new VideoDownload(content, zipFilename(video));
  }

  private String zipFilename(Video video) {
    String originalFilename = video.getOriginalFilename();
    int dotIndex = originalFilename.lastIndexOf('.');
    String baseName = dotIndex > 0 ? originalFilename.substring(0, dotIndex) : originalFilename;
    return baseName + ".zip";
  }
}
