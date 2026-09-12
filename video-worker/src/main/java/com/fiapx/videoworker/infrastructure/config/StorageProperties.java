package com.fiapx.videoworker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Local (MinIO): endpoint próprio, chaves estáticas e path-style. AWS (perfil {@code aws}):
 * endpoint e chaves vazios — o SDK usa o endpoint regional e a cadeia padrão de credenciais
 * (no EKS, o instance profile do nó) — e virtual-hosted style.
 */
@ConfigurationProperties("fiapx.storage")
public record StorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucketRaw,
    String bucketProcessed,
    @DefaultValue("us-east-1") String region,
    @DefaultValue("true") boolean pathStyle
) {

  public boolean hasEndpointOverride() {
    return endpoint != null && !endpoint.isBlank();
  }

  public boolean hasStaticCredentials() {
    return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
  }
}
