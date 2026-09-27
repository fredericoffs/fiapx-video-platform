package com.fiapx.videoapi.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fiapx.videoapi.application.usecase.RequestVideoProcessingUseCase;
import com.fiapx.videoapi.domain.model.OutboxEvent;
import com.fiapx.videoapi.domain.port.MessagePublisher;
import com.fiapx.videoapi.domain.port.OutboxEventRepository;
import com.fiapx.videoapi.infrastructure.config.OutboxProperties;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** O job roda fora de requisição: o correlationId do evento tem que estar no MDC durante a publicação. */
class OutboxPublisherJobTest {

  @Test
  void restoresTheEventCorrelationIdWhilePublishingAndClearsItAfterwards() {
    OutboxEventRepository repository = mock(OutboxEventRepository.class);
    MessagePublisher publisher = mock(MessagePublisher.class);
    OutboxEvent event = OutboxEvent.newEvent(UUID.randomUUID(),
        RequestVideoProcessingUseCase.EVENT_TYPE_VIDEO_UPLOAD_REQUESTED, "{}", "corr-do-upload");
    when(repository.claimUnpublished(anyInt(), any(Duration.class))).thenReturn(List.of(event), List.of());
    AtomicReference<String> seenWhilePublishing = new AtomicReference<>();
    doAnswer(invocation -> {
      seenWhilePublishing.set(MDC.get("correlationId"));
      return null;
    }).when(publisher).publish(eq("fiapx-video-processing"), any());
    OutboxPublisherJob job = new OutboxPublisherJob(repository, publisher, new OutboxProperties(3000, 10),
        new QueueProperties("fiapx-video-processing", "status", "notification", null, null, null));

    job.publishPending();

    assertThat(seenWhilePublishing.get()).isEqualTo("corr-do-upload");
    assertThat(MDC.get("correlationId")).isNull();
  }
}
