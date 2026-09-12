package com.fiapx.videoworker.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fiapx.videoworker.application.usecase.ProcessVideoUseCase;
import com.fiapx.videoworker.domain.port.MessagePublisher;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

/** JSON inválido não pode entrar em loop de retry: vai direto para a DLQ (listener principal) ou é descartado (listener da DLQ). */
class MalformedMessageHandlingTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final QueueProperties queueProperties = new QueueProperties("video.processing", "video.status-updates",
      "video.processing.dlx", "video.processing.dlq", "video.status-updates.dlx", "video.status-updates.dlq");
  private final ProcessVideoUseCase processVideoUseCase = mock(ProcessVideoUseCase.class);
  private final MessagePublisher messagePublisher = mock(MessagePublisher.class);

  @Test
  void processingListenerRejectsMalformedJsonWithoutRequeue() {
    VideoProcessingListener listener = new VideoProcessingListener(processVideoUseCase, messagePublisher, objectMapper,
        queueProperties);

    assertThatThrownBy(() -> listener.onMessage("{isto nao e json", "corr-1"))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);

    verifyNoInteractions(processVideoUseCase);
    verify(messagePublisher, never()).publish(anyString(), anyString(), any());
  }

  @Test
  void deadLetterListenerDiscardsMalformedJsonWithoutPublishingAResult() {
    VideoProcessingDeadLetterListener listener = new VideoProcessingDeadLetterListener(messagePublisher, objectMapper,
        queueProperties);

    listener.onMessage("{isto nao e json", null);

    verify(messagePublisher, never()).publish(anyString(), anyString(), any());
  }
}
