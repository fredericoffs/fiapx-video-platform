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
import com.fiapx.videoapi.infrastructure.messaging.sqs.SqsTestSupport;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Percorro aqui a cadeia completa descrita na Sprint 3 (item 3) em um único cenário:
 * upload HTTP → linha na outbox → OutboxPublisherJob publica em fiapx-video-processing →
 * consumo simulado de fiapx-video-status-updates (papel do video-worker) → status aplicado.
 * Os testes existentes (VideoUploadIntegrationTest, OutboxPublisherJobIntegrationTest)
 * cobrem cada etapa isoladamente, mas nenhum encadeava as três em um só fluxo — por isso
 * escrevi este. O papel do video-worker é simulado aqui; a cadeia real (ffmpeg, zip, download
 * e notificação de falha) é exercitada contra o ambiente implantado por scripts/aws-e2e-smoke.sh.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EndToEndVideoProcessingFlowIntegrationTest extends AbstractSqsIntegrationTest {

  static final SqsClient SQS = SqsTestSupport.client();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SpringDataVideoRepository videoRepository;

  @Autowired
  private SpringDataOutboxEventRepository springDataOutboxEventRepository;

  @Autowired
  private OutboxPublisherJob outboxPublisherJob;

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

    // O @Scheduled do job pode ter publicado antes da chamada acima e ainda não ter gravado o markPublished.
    UUID eventId = events.getFirst().getId();
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        assertThat(springDataOutboxEventRepository.findById(eventId).orElseThrow().isPublished()).isTrue());

    String zipStorageKey = "processed/" + videoId + ".zip";
    ProcessingResultMessage resultMessage = new ProcessingResultMessage(ProcessingEventType.PROCESSING_COMPLETED,
        videoId, zipStorageKey, null);
    String statusUpdatesUrl = SqsTestSupport.urlOf(SQS, queueProperties.statusUpdates());
    SQS.sendMessage(b -> b.queueUrl(statusUpdatesUrl).messageBody(objectMapper.writeValueAsString(resultMessage)));

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
    String url = SqsTestSupport.urlOf(SQS, queue);
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      for (Message message : SQS.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1))
          .messages()) {
        if (message.body().contains(needle)) {
          return message;
        }
      }
    }
    return null;
  }
}
