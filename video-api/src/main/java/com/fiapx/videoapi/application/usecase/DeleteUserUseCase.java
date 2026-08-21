package com.fiapx.videoapi.application.usecase;

import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.domain.port.VideoRepository;
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

  public void handle(UUID userId) {
    if (userRepository.findById(userId).isEmpty()) {
      throw new UserNotFoundException(userId);
    }

    // Sempre pega a página 0 — cada vídeo excluído libera espaço pro próximo lote,
    // exclusão real e em cascata (decisão do usuário), não soft-delete.
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
