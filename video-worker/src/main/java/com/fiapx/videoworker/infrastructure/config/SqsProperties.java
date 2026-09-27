package com.fiapx.videoworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Perfil aws: endpoint e chaves vazios — endpoint regional e cadeia padrão de credenciais
 * (instance profile do nó no EKS). Endpoint/chaves só são usados nos testes (LocalStack).
 */
@ConfigurationProperties("fiapx.sqs")
public record SqsProperties(
    @DefaultValue("us-east-1") String region,
    String endpoint,
    String accessKey,
    String secretKey,
    @DefaultValue("20") int waitTimeSeconds,
    @DefaultValue("30") int heartbeatSeconds,
    @DefaultValue("120") int visibilityExtensionSeconds,
    @DefaultValue("30000") long depthPollMillis,
    // Prazo pra terminar a mensagem em andamento no desligamento (scale-down, deploy): cobre o
    // timeout do ffmpeg (15 min). terminationGracePeriodSeconds do Deployment fica acima dele.
    @DefaultValue("960") int shutdownDrainSeconds
) {

  public boolean hasEndpointOverride() {
    return endpoint != null && !endpoint.isBlank();
  }

  public boolean hasStaticCredentials() {
    return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
  }
}
