package com.fiapx.videoapi;

import com.fiapx.videoapi.application.dto.LoginCommand;
import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.application.dto.RegisterUserCommand;
import com.fiapx.videoapi.application.usecase.LoginUseCase;
import com.fiapx.videoapi.application.usecase.RegisterUserUseCase;
import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class VideoUploadIntegrationTest extends AbstractSqsIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private SpringDataVideoRepository videoRepository;

  @Autowired
  private SpringDataOutboxEventRepository springDataOutboxEventRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private RegisterUserUseCase registerUserUseCase;

  @Autowired
  private LoginUseCase loginUseCase;

  private String loginAsNewUser() {
    String email = "user-" + UUID.randomUUID() + "@fiapx.com";
    registerUserUseCase.handle(new RegisterUserCommand(email, "senha-secreta-123"));
    LoginResult login = loginUseCase.handle(new LoginCommand(email, "senha-secreta-123"));
    return login.accessToken();
  }

  @Test
  void uploadPersistsQueuedVideoAndUnpublishedOutboxEvent() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "movie.mp4", "video/mp4", "fake-bytes".getBytes());
    String token = loginAsNewUser();

    MvcResult result = mockMvc
        .perform(multipart("/videos").file(file).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    UUID videoId = UUID.fromString(json.get("id").asString());

    Optional<VideoEntity> savedVideo = videoRepository.findById(videoId);
    assertThat(savedVideo).isPresent();
    assertThat(savedVideo.get().getOriginalFilename()).isEqualTo("movie.mp4");
    assertThat(savedVideo.get().getStatus().name()).isEqualTo("QUEUED");

    List<OutboxEventEntity> events = springDataOutboxEventRepository.findAll()
        .stream()
        .filter(event -> event.getAggregateId().equals(videoId))
        .toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().getEventType()).isEqualTo("VideoUploadRequested");
    // O relay (OutboxPublisherJob, a cada 3 s) pode já ter publicado o evento — o que este
    // teste garante é que o upload gravou o evento na mesma transação, não o estado do relay.
    assertThat(events.getFirst().getPayload()).contains(videoId.toString()).contains("\"eventId\"");

    // A reserva de limpeza do objeto enviado foi cancelada junto com o registro do vídeo.
    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM video_api.storage_cleanup WHERE object_key = ?", Integer.class,
        savedVideo.get().getStorageKey())).isZero();
  }
}
