package com.fiapx.notificationworker.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fiapx.notificationworker.application.dto.NotificationRequestedMessage;
import com.fiapx.notificationworker.domain.exception.NotificationDeliveryException;
import com.fiapx.notificationworker.domain.model.NotificationAttempt;
import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.port.NotificationAttemptRepository;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import com.fiapx.notificationworker.infrastructure.config.NotificationProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SendFailureNotificationUseCaseTest {

  private final NotificationChannel emailChannel = channelMock(NotificationChannelType.EMAIL);
  private final NotificationChannel webhookChannel = channelMock(NotificationChannelType.WEBHOOK);
  private final NotificationAttemptRepository notificationAttemptRepository =
      mock(NotificationAttemptRepository.class);
  private final SendFailureNotificationUseCase useCase = new SendFailureNotificationUseCase(
      List.of(emailChannel, webhookChannel), notificationAttemptRepository,
      new NotificationProperties("no-reply@fiapx.local", "http://webhook", Duration.ofSeconds(5)));

  private static NotificationChannel channelMock(NotificationChannelType type) {
    NotificationChannel channel = mock(NotificationChannel.class);
    when(channel.type()).thenReturn(type);
    return channel;
  }

  private static NotificationAttempt claim(NotificationChannelType type) {
    return NotificationAttempt.claiming(UUID.randomUUID(), type);
  }

  @Test
  void skipsAlreadySentEmailAndDoesNotTryWebhook() {
    UUID videoId = UUID.randomUUID();
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.EMAIL))
        .thenReturn(Optional.empty());

    when(notificationAttemptRepository.isSent(videoId, NotificationChannelType.EMAIL)).thenReturn(true);
    useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com"));

    verify(emailChannel, never()).send(any(), any(), any());
    verify(webhookChannel, never()).send(any(), any(), any());
    verify(notificationAttemptRepository, never()).markSent(any());
  }

  @Test
  void marksAttemptSentWhenEmailSucceeds() {
    UUID videoId = UUID.randomUUID();
    NotificationAttempt attempt = claim(NotificationChannelType.EMAIL);
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.EMAIL))
        .thenReturn(Optional.of(attempt));
    when(emailChannel.send(videoId, "erro", "user@example.com"))
        .thenReturn(CompletableFuture.completedFuture(null));

    useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com"));

    verify(notificationAttemptRepository).markSent(attempt.getId());
    verify(webhookChannel, never()).send(any(), any(), any());
  }

  @Test
  void alertsOperatorsByWebhookButStillRethrowsSoTheEmailIsRetried() {
    UUID videoId = UUID.randomUUID();
    NotificationAttempt emailAttempt = claim(NotificationChannelType.EMAIL);
    NotificationAttempt webhookAttempt = claim(NotificationChannelType.WEBHOOK);
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.EMAIL))
        .thenReturn(Optional.of(emailAttempt));
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.WEBHOOK))
        .thenReturn(Optional.of(webhookAttempt));
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("smtp indisponível"));
    when(webhookChannel.send(videoId, "erro", "user@example.com"))
        .thenReturn(CompletableFuture.completedFuture(null));

    // Sucesso do webhook não conclui a notificação: o usuário ainda não recebeu nada.
    assertThatThrownBy(() -> useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class)
        .hasMessageContaining("não entregue ao usuário");

    verify(notificationAttemptRepository).markFailed(eq(emailAttempt.getId()), contains("smtp indisponível"));
    verify(notificationAttemptRepository).markSent(webhookAttempt.getId());
  }

  @Test
  void doesNotRetryWebhookWhenAlreadySent() {
    UUID videoId = UUID.randomUUID();
    NotificationAttempt emailAttempt = claim(NotificationChannelType.EMAIL);
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.EMAIL))
        .thenReturn(Optional.of(emailAttempt));
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.WEBHOOK))
        .thenReturn(Optional.empty());
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("smtp indisponível"));
    when(notificationAttemptRepository.isSent(videoId, NotificationChannelType.WEBHOOK)).thenReturn(true);

    // Reentrega: o e-mail é tentado de novo, o alerta já enviado não se repete.
    assertThatThrownBy(() -> useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class);

    verify(emailChannel).send(videoId, "erro", "user@example.com");
    verify(webhookChannel, never()).send(any(), any(), any());
  }

  @Test
  void webhookClaimInProgressDoesNotHideTheEmailFailure() {
    UUID videoId = UUID.randomUUID();
    NotificationAttempt emailAttempt = claim(NotificationChannelType.EMAIL);
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.EMAIL))
        .thenReturn(Optional.of(emailAttempt));
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.WEBHOOK))
        .thenReturn(Optional.empty());
    when(notificationAttemptRepository.isSent(videoId, NotificationChannelType.WEBHOOK)).thenReturn(false);
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("smtp indisponível"));

    assertThatThrownBy(() -> useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class)
        .hasMessageContaining("não entregue ao usuário");
  }

  @Test
  void rethrowsWhenBothChannelsFail() {
    UUID videoId = UUID.randomUUID();
    NotificationAttempt emailAttempt = claim(NotificationChannelType.EMAIL);
    NotificationAttempt webhookAttempt = claim(NotificationChannelType.WEBHOOK);
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.EMAIL))
        .thenReturn(Optional.of(emailAttempt));
    when(notificationAttemptRepository.tryClaim(videoId, NotificationChannelType.WEBHOOK))
        .thenReturn(Optional.of(webhookAttempt));
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("smtp indisponível"));
    when(webhookChannel.send(videoId, "erro", "user@example.com")).thenReturn(failedFuture("webhook indisponível"));

    assertThatThrownBy(
        () -> useCase.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class);

    verify(notificationAttemptRepository).markFailed(eq(emailAttempt.getId()), contains("smtp indisponível"));
    verify(notificationAttemptRepository).markFailed(eq(webhookAttempt.getId()), contains("webhook indisponível"));
  }

  // Item 10 da revisão crítica: um destino que aceita a conexão e nunca responde não pode
  // prender o dispatcher indefinidamente — .get(timeout) tem que valer mesmo quando o canal
  // nunca completa (nem com sucesso, nem com falha).
  @Test
  void fallsBackToWebhookWhenEmailNeverCompletesWithinTheTimeout() {
    UUID videoId = UUID.randomUUID();
    NotificationAttempt emailAttempt = claim(NotificationChannelType.EMAIL);
    NotificationAttempt webhookAttempt = claim(NotificationChannelType.WEBHOOK);
    NotificationAttemptRepository repository = mock(NotificationAttemptRepository.class);
    when(repository.tryClaim(videoId, NotificationChannelType.EMAIL)).thenReturn(Optional.of(emailAttempt));
    when(repository.tryClaim(videoId, NotificationChannelType.WEBHOOK)).thenReturn(Optional.of(webhookAttempt));
    when(emailChannel.send(videoId, "erro", "user@example.com")).thenReturn(new CompletableFuture<>());
    when(webhookChannel.send(videoId, "erro", "user@example.com"))
        .thenReturn(CompletableFuture.completedFuture(null));
    SendFailureNotificationUseCase useCaseWithShortTimeout = new SendFailureNotificationUseCase(
        List.of(emailChannel, webhookChannel), repository,
        new NotificationProperties("no-reply@fiapx.local", "http://webhook", Duration.ofMillis(50)));

    assertThatThrownBy(() -> useCaseWithShortTimeout.handle(new NotificationRequestedMessage(videoId, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class);
    verify(repository, never()).markFailed(any(), any());
    verify(webhookChannel, never()).send(any(), any(), any());
  }

  @Test
  void pendingClaimIsNotAcknowledgedAsDelivered() {
    var id = UUID.randomUUID();
    when(notificationAttemptRepository.tryClaim(id, NotificationChannelType.EMAIL)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.handle(new NotificationRequestedMessage(id, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class);
    verify(webhookChannel, never()).send(any(), any(), any());
  }

  @Test
  void lateSuccessAfterTimeoutIsRecordedWithoutStartingFallback() {
    var id = UUID.randomUUID();
    var attempt = claim(NotificationChannelType.EMAIL);
    var pending = new CompletableFuture<Void>();
    when(notificationAttemptRepository.tryClaim(id, NotificationChannelType.EMAIL)).thenReturn(Optional.of(attempt));
    when(emailChannel.send(any(), any(), any())).thenReturn(pending);
    var shortTimeout = new SendFailureNotificationUseCase(List.of(emailChannel, webhookChannel), notificationAttemptRepository,
        new NotificationProperties("from@example.com", "http://webhook", Duration.ofMillis(10)));
    assertThatThrownBy(() -> shortTimeout.handle(new NotificationRequestedMessage(id, "erro", "user@example.com")))
        .isInstanceOf(NotificationDeliveryException.class);
    pending.complete(null);
    verify(notificationAttemptRepository).markSent(attempt.getId());
    verify(webhookChannel, never()).send(any(), any(), any());
  }

  private static CompletableFuture<Void> failedFuture(String message) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException(message));
    return future;
  }
}
