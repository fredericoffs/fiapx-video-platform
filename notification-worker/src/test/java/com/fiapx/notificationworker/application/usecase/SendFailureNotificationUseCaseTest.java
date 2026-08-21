package com.fiapx.notificationworker.application.usecase;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.domain.exception.NotificationDeliveryException;
import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.model.NotificationStatus;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendFailureNotificationUseCaseTest {

  private final NotificationChannel emailChannel = mock(NotificationChannel.class);
  private final NotificationAttemptRepository notificationAttemptRepository =
      mock(NotificationAttemptRepository.class);
  private final SendFailureNotificationUseCase useCase =
      new SendFailureNotificationUseCase(emailChannel, notificationAttemptRepository);

  @Test
  void skipsAlreadySentNotification() {
    UUID videoId = UUID.randomUUID();
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.EMAIL)).thenReturn(true);

    useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com"));

    verify(emailChannel, never()).send(any(), any(), any());
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
    assertThat(captor.getValue().getVideoId()).isEqualTo(videoId);
  }

  @Test
  void savesFailedAttemptAndRethrowsWhenEmailFails() {
    UUID videoId = UUID.randomUUID();
    when(notificationAttemptRepository.existsSent(videoId, NotificationChannelType.EMAIL)).thenReturn(false);
    CompletableFuture<Void> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("smtp indisponível"));
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(failed);

    assertThatThrownBy(
        () -> useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class);

    ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
    verify(notificationAttemptRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannelType.EMAIL);
  }
}
