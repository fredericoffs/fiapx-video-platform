package com.fiapx.videoworker;

import org.springframework.boot.test.context.TestConfiguration;

// video-worker é stateless (ADR-008) e a mensageria é só SQS/LocalStack (SqsTestSupport) —
// sem Testcontainers gerenciando conexão nenhuma aqui.
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

}
