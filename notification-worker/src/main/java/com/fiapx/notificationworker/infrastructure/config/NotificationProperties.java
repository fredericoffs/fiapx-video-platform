package com.fiapx.notificationworker.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("fiapx.notification")
public record NotificationProperties(
    String fromAddress,
    String webhookFallbackUrl,
    /**
     * Prazo máximo que o dispatcher espera por um canal (SendFailureNotificationUseCase#tryChannel).
     * Circuit breaker/bulkhead não substituem isso: protegem o canal de sobrecarga, mas não têm
     * prazo de execução próprio — sem este teto, um destino que aceita a conexão e nunca responde
     * prende o consumidor indefinidamente (visibilidade da mensagem só é reestendida, nunca some).
     */
    @DefaultValue("20s") Duration channelTimeout
) {

}
