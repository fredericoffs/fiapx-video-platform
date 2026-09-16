package com.fiapx.videoapi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  // Versão fixada (item 20 da revisão crítica): "latest" muda de imagem sem aviso — um
  // major novo do Postgres podia quebrar teste sem nenhuma mudança no código. 17 é a mesma
  // major do RDS de produção (db_engine_version em k8s/terraform/aws/variables.tf).
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
  }

  // Mesmo motivo do Postgres acima — 7 é a mesma major do ElastiCache de produção
  // (redis_engine_version em k8s/terraform/aws/variables.tf).
  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
  }

}
