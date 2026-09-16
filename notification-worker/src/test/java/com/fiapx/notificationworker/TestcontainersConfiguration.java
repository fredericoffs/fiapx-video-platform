package com.fiapx.notificationworker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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

}
