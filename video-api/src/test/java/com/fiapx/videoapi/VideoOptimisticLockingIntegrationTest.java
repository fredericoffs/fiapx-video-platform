package com.fiapx.videoapi;

import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Simula duas atualizações concorrentes na mesma linha (dois `findById` independentes,
 * cada `save` em sua própria transação de repositório) para provar que o `@Version` de
 * VideoEntity realmente rejeita a segunda escrita baseada em estado obsoleto.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoOptimisticLockingIntegrationTest {

  @Autowired
  private SpringDataVideoRepository videoRepository;

  @Test
  void secondConcurrentUpdateFailsWithOptimisticLockException() {
    VideoEntity entity = new VideoEntity();
    entity.setId(UUID.randomUUID());
    entity.setOriginalFilename("movie.mp4");
    entity.setStorageKey("raw/movie.mp4");
    entity.setStatus(VideoStatus.QUEUED);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    videoRepository.save(entity);

    VideoEntity firstReader = videoRepository.findById(entity.getId()).orElseThrow();
    VideoEntity secondReader = videoRepository.findById(entity.getId()).orElseThrow();

    firstReader.setStatus(VideoStatus.COMPLETED);
    firstReader.setZipStorageKey("processed/" + entity.getId() + ".zip");
    videoRepository.save(firstReader);

    secondReader.setStatus(VideoStatus.FAILED);
    secondReader.setErrorMessage("não deveria vencer a corrida");

    assertThatThrownBy(() -> videoRepository.save(secondReader))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    VideoEntity finalState = videoRepository.findById(entity.getId()).orElseThrow();
    assertThat(finalState.getStatus()).isEqualTo(VideoStatus.COMPLETED);
    assertThat(finalState.getZipStorageKey()).isEqualTo("processed/" + entity.getId() + ".zip");
  }
}
