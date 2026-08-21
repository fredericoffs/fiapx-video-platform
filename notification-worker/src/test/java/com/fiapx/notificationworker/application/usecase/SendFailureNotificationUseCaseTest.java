package com.fiapx.notificationworker.application.usecase;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.domain.exception.NotificationDeliveryException;
import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendFailureNotificationUseCaseTest {

  private final NotificationChannel emailChannel = channelMock(NotificationChannelType.EMAIL);
  private final NotificationChannel webhookChannel = channelMock(NotificationChannelType.WEBHOOK);
  private final NotificationAttemptRepository notificationAttemptRepository =
      mock(NotificationAttemptRepository.class);
  private final SendFailureNotificationUseCase useCase =
      new SendFailureNotificationUseCase(List.of(emailChannel, webhookChannel), notificationAttemptRepository);

  private static NotificationChannel channelMock(NotificationChannelType type) {
    NotificationChannel channel = mock(NotificationChannel.class);
    when(channel.type()).thenReturn(type);
    return channel;
  }

  @Test
  void skipsAlreadySentEmailAndDoesNotTryWebhook() {
    UUID videoId = UUID.randomUUID();
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.EMAIL)).thenReturn(true);

    useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com"));

    verify(emailChannel, never()).send(any(), any(), any());
    verify(webhookChannel, never()).send(any(), any(), any());
    verify(notificationAttemptRepository, never()).save(any());
  }

  @Test
  void savesSentAttemptWhenEmailSucceeds() {
    UUID videoId = UUID.randomUUID();
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.EMAIL)).thenReturn(false);
    when(emailChannel.send(videoId, "erro", "user@example.com"))
        .thenReturn(CompletableFuture.completedFuture(null));

    useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com"));

    ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
    verify(notificationAttemptRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannelType.EMAIL);
    verify(webhookChannel, never()).send(any(), any(), any());
  }

  @Test
  void fallsBackToWebhookWhenEmailFails() {
    UUID videoId = UUID.randomUUID();
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.EMAIL)).thenReturn(false);
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.WEBHOOK)).thenReturn(false);
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("smtp indisponível"));
    when(webhookChannel.send(videoId, "erro", "user@example.com"))
        .thenReturn(CompletableFuture.completedFuture(null));

    useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com"));

    ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
    verify(notificationAttemptRepository, times(2)).save(captor.capture());
    List<NotificationAttempt> saved = captor.getAllValues();
    assertThat(saved.get(0).getChannel()).isEqualTo(NotificationChannelType.EMAIL);
    assertThat(saved.get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
    assertThat(saved.get(1).getChannel()).isEqualTo(NotificationChannelType.WEBHOOK);
    assertThat(saved.get(1).getStatus()).isEqualTo(NotificationStatus.SENT);
  }

  @Test
  void doesNotRetryWebhookWhenAlreadySent() {
    UUID videoId = UUID.randomUUID();
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.EMAIL)).thenReturn(false);
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.WEBHOOK)).thenReturn(true);
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("smtp indisponível"));

    useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com"));

    verify(webhookChannel, never()).send(any(), any(), any());
  }

  @Test
  void rethrowsWhenBothChannelsFail() {
    UUID videoId = UUID.randomUUID();
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.EMAIL)).thenReturn(false);
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.WEBHOOK)).thenReturn(false);
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("smtp indisponível"));
    when(webhookChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("webhook indisponível"));

    assertThatThrownBy(
        () -> useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class);

    ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
    verify(notificationAttemptRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(NotificationStatus.FAILED));
  }

  private static CompletableFuture<Void> failedFuture(String message) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException(message));
    return future;
  }
}
