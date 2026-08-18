package com.fiapx.videoapi;

import com.fiapx.videoapi.application.dto.LoginCommand;
import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.application.dto.RegisterUserCommand;
import com.fiapx.videoapi.application.event.ProcessingEventType;
import com.fiapx.videoapi.application.event.ProcessingResultMessage;
import com.fiapx.videoapi.application.job.OutboxPublisherJob;
import com.fiapx.videoapi.application.usecase.LoginUseCase;
import com.fiapx.videoapi.application.usecase.RegisterUserUseCase;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.infrastructure.config.QueueProperties;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Percorre a cadeia completa descrita na Sprint 3 (item 3) em um único cenário:
 * upload HTTP → linha na outbox → OutboxPublisherJob publica em video.processing →
 * consumo simulado de video.status-updates (papel do video-worker) → status aplicado.
 * Os testes existentes (VideoUploadIntegrationTest, OutboxPublisherJobIntegrationTest,
 * VideoStatusUpdateListenerIntegrationTest) cobrem cada etapa isoladamente, mas nenhum
 * encadeava as quatro em um só fluxo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EndToEndVideoProcessingFlowIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SpringDataVideoRepository videoRepository;

  @Autowired
  private SpringDataOutboxEventRepository springDataOutboxEventRepository;

  @Autowired
  private OutboxPublisherJob outboxPublisherJob;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private QueueProperties queueProperties;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RegisterUserUseCase registerUserUseCase;

  @Autowired
  private LoginUseCase loginUseCase;

  @Test
  void uploadFlowsThroughOutboxAndStatusUpdateUntilVideoIsCompleted() throws Exception {
    String token = registerAndLogin();
    MockMultipartFile file = new MockMultipartFile("file", "movie.mp4", "video/mp4", "fake-bytes".getBytes());

    MvcResult uploadResult = mockMvc
        .perform(multipart("/videos").file(file).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andReturn();
    JsonNode json = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
    UUID videoId = UUID.fromString(json.get("id").asString());

    List<OutboxEventEntity> events = springDataOutboxEventRepository.findAll().stream()
        .filter(event -> event.getAggregateId().equals(videoId))
        .toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().isPublished()).isFalse();

    outboxPublisherJob.publishPending();

    Message processingMessage = receiveContaining(queueProperties.processing(), videoId.toString());
    assertThat(processingMessage)
        .as("mensagem publicada na fila %s contendo %s", queueProperties.processing(), videoId)
        .isNotNull();

    OutboxEventEntity publishedEvent = springDataOutboxEventRepository.findById(events.getFirst().getId())
        .orElseThrow();
    assertThat(publishedEvent.isPublished()).isTrue();

    String zipStorageKey = "processed/" + videoId + ".zip";
    ProcessingResultMessage resultMessage = new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED,
        videoId, zipStorageKey, null);
    rabbitTemplate.convertAndSend(queueProperties.statusUpdates(), objectMapper.writeValueAsString(resultMessage));

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      VideoEntity updated = videoRepository.findById(videoId).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(VideoStatus.COMPLETED);
      assertThat(updated.getZipStorageKey()).isEqualTo(zipStorageKey);
    });
  }

  private String registerAndLogin() {
    String email = "user-" + UUID.randomUUID() + "@fiapx.com";
    registerUserUseCase.handle(new RegisterUserCommand(email, "senha-secreta-123"));
    LoginResult login = loginUseCase.handle(new LoginCommand(email, "senha-secreta-123"));
    return login.accessToken();
  }

  private Message receiveContaining(String queue, String needle) {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      Message message = rabbitTemplate.receive(queue, 500);
      if (message != null && new String(message.getBody()).contains(needle)) {
        return message;
      }
    }
    return null;
  }
}
