package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.exception.VideoBeingProcessedException;
import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeleteUserUseCase {

  private static final int BATCH_SIZE = 50;

  private final UserRepository userRepository;
  private final VideoRepository videoRepository;
  private final DeleteVideoUseCase deleteVideoUseCase;

  public DeleteUserUseCase(
      UserRepository userRepository,
      VideoRepository videoRepository,
      DeleteVideoUseCase deleteVideoUseCase
  ) {
    this.userRepository = userRepository;
    this.videoRepository = videoRepository;
    this.deleteVideoUseCase = deleteVideoUseCase;
  }

  @org.springframework.transaction.annotation.Transactional
  public void handle(UUID userId) {
    if (userRepository.findByIdForUpdate(userId).isEmpty()) {
      throw new UserNotFoundException(userId);
    }

    for (var status : List.of(VideoStatus.QUEUED, VideoStatus.PROCESSING)) {
      PageResult<Video> active = videoRepository.findByUserId(userId, status, 0, 1);
      if (!active.items().isEmpty()) {
        throw new VideoBeingProcessedException(active.items().getFirst().getId());
      }
    }

    // Sempre pego a página 0 — cada vídeo excluído libera espaço pro próximo lote.
    // Faço exclusão real e em cascata (decisão do usuário), não soft-delete.
    PageResult<Video> videos = videoRepository.findByUserId(userId, null, 0, BATCH_SIZE);
    while (!videos.items().isEmpty()) {
      for (Video video : videos.items()) {
        deleteVideoUseCase.handle(video.getId(), userId, true);
      }
      videos = videoRepository.findByUserId(userId, null, 0, BATCH_SIZE);
    }

    userRepository.deleteById(userId);
  }
}
