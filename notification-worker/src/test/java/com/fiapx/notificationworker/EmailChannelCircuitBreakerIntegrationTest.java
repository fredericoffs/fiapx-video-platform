package com.fiapx.notificationworker;

import com.fiapx.notificationworker.domain.port.NotificationChannel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SMTP apontado para uma porta local sem nenhum servidor escutando — toda tentativa
 * de envio falha rápido (conexão recusada), sem precisar de um GreenMail derrubado
 * de propósito. Limiares do circuito rebaixados só para este teste, via
 * {@link TestPropertySource}, para abrir o circuito com poucas chamadas.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "spring.mail.host=127.0.0.1",
    "spring.mail.port=1",
    "spring.mail.properties.mail.smtp.connectiontimeout=500",
    "spring.mail.properties.mail.smtp.timeout=500",
    "spring.mail.properties.mail.smtp.writetimeout=500",
    "resilience4j.circuitbreaker.instances.email-channel.sliding-window-size=4",
    "resilience4j.circuitbreaker.instances.email-channel.minimum-number-of-calls=4",
    "resilience4j.circuitbreaker.instances.email-channel.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.email-channel.wait-duration-in-open-state=10s"
})
class EmailChannelCircuitBreakerIntegrationTest {

  @Autowired
  private NotificationChannel emailChannel;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @Test
  void circuitOpensAfterRepeatedSmtpFailures() {
    UUID videoId = UUID.randomUUID();

    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> emailChannel.send(videoId, "erro", "user@example.com").join())
          .isInstanceOf(CompletionException.class);
    }

    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("email-channel");
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // Com o circuito aberto, a próxima chamada é rejeitada na hora, sem tentar
    // conectar de novo no SMTP — mesmo comportamento observável de fora (via o
    // fallback), então não distinguimos os dois casos no teste, só confirmamos que
    // o circuito realmente abriu.
    assertThatThrownBy(() -> emailChannel.send(videoId, "erro", "user@example.com").join())
        .isInstanceOf(CompletionException.class);
  }
}
