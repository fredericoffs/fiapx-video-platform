package com.fiapx.videoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fiapx.videoapi.domain.model.OutboundMessage;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.MessagePublisher;
import com.fiapx.videoapi.domain.port.VideoRepository;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsMessagePublisher;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Fim a fim contra SQS real (LocalStack): resultado do worker chega por SQS e atualiza o vídeo. */
@SpringBootTest
class AwsProfileIntegrationTest extends AbstractSqsIntegrationTest {

  @Autowired
  private MessagePublisher messagePublisher;

  @Autowired
  private VideoRepository videoRepository;

  @Autowired
  private QueueProperties queueProperties;

  @Test
  void contextUsesSqsAdapters() {
    assertThat(messagePublisher).isInstanceOf(SqsMessagePublisher.class);
    assertThat(queueProperties.statusUpdates()).isEqualTo("fiapx-video-status-updates");
  }

  @Test
  void processingEventsArrivingBySqsMoveTheVideoThroughProcessingToCompleted() {
    // user_id é nullable — sem usuário evita a FK e o teste foca só no caminho SQS → status.
    Video video = Video.newQueued(UUID.randomUUID(), null, "movie.mp4", "raw/x/source.mp4");
    videoRepository.save(video);

    messagePublisher.publish(queueProperties.statusUpdates(), OutboundMessage.of(
        "{\"eventType\":\"ProcessingStarted\",\"videoId\":\"" + video.getId() + "\"}", "corr-aws", "evt-1"));
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        assertThat(videoRepository.findById(video.getId()).orElseThrow().getStatus()).isEqualTo(VideoStatus.PROCESSING));

    messagePublisher.publish(queueProperties.statusUpdates(), OutboundMessage.of(
        "{\"eventType\":\"ProcessingCompleted\",\"videoId\":\"" + video.getId()
            + "\",\"zipStorageKey\":\"processed/x/x.zip\"}", "corr-aws", "evt-1"));
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
      Video updated = videoRepository.findById(video.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(VideoStatus.COMPLETED);
      assertThat(updated.getZipStorageKey()).isEqualTo("processed/x/x.zip");
    });
  }
}
