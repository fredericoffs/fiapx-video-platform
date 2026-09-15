package com.fiapx.videoapi;

import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.StorageClient;
import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthAndVideoOwnershipIntegrationTest extends AbstractSqsIntegrationTest {

  private static final String PASSWORD = "senha-secreta-123";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private SpringDataVideoRepository videoRepository;

  @Autowired
  private StorageClient storageClient;

  @Autowired
  private StorageProperties storageProperties;

  @Test
  void loginWithWrongPasswordReturns401AndWithRightPasswordReturnsJwt() throws Exception {
    String email = newEmail();
    register(email);

    mockMvc.perform(loginRequest(email, "senha-errada")).andExpect(status().isUnauthorized());

    MvcResult result = mockMvc.perform(loginRequest(email, PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("accessToken").asString()).isNotBlank();
  }

  @Test
  void uploadWithoutTokenIsRejected() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "movie.mp4", "video/mp4", "fake-bytes".getBytes());

    mockMvc.perform(multipart("/videos").file(file)).andExpect(status().isUnauthorized());
  }

  @Test
  void userCannotSeeOrDownloadAnotherUsersVideo() throws Exception {
    String tokenA = registerAndLogin(newEmail());
    String tokenB = registerAndLogin(newEmail());

    UUID videoId = uploadVideo(tokenA);

    mockMvc.perform(get("/videos/" + videoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/videos/" + videoId + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/videos/" + videoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
        .andExpect(status().isOk());
  }

  @Test
  void listVideosReturnsOnlyOwnVideosPaginated() throws Exception {
    String tokenA = registerAndLogin(newEmail());
    String tokenB = registerAndLogin(newEmail());

    uploadVideo(tokenA);
    uploadVideo(tokenA);
    uploadVideo(tokenB);

    MvcResult result = mockMvc
        .perform(get("/videos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("totalElements").asLong()).isEqualTo(2);
    assertThat(json.get("items").size()).isEqualTo(2);
  }

  @Test
  void downloadReturnsZipOnlyWhenVideoIsCompleted() throws Exception {
    String token = registerAndLogin(newEmail());
    UUID videoId = uploadVideo(token);

    mockMvc.perform(get("/videos/" + videoId + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isConflict());

    markCompletedWithZip(videoId, "conteudo-zip-fake".getBytes());

    MvcResult result = mockMvc
        .perform(get("/videos/" + videoId + "/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo("conteudo-zip-fake".getBytes());
  }

  @Test
  void loginIsBlockedAfterTooManyFailedAttempts() throws Exception {
    String email = newEmail();
    register(email);

    for (int i = 0; i < 5; i++) {
      mockMvc.perform(loginRequest(email, "senha-errada")).andExpect(status().isUnauthorized());
    }

    mockMvc.perform(loginRequest(email, PASSWORD)).andExpect(status().isTooManyRequests());
  }

  private void markCompletedWithZip(UUID videoId, byte[] zipBytes) {
    VideoEntity entity = videoRepository.findById(videoId).orElseThrow();
    String zipKey = "processed/" + videoId + ".zip";
    entity.setStatus(VideoStatus.COMPLETED);
    entity.setZipStorageKey(zipKey);
    videoRepository.save(entity);
    storageClient.upload(
        storageProperties.bucketProcessed(), zipKey, new ByteArrayInputStream(zipBytes), zipBytes.length,
        "application/zip"
    );
  }

  private String newEmail() {
    return "user-" + UUID.randomUUID() + "@fiapx.com";
  }

  private void register(String email) throws Exception {
    String body = objectMapper.writeValueAsString(new RegisterPayload(email, PASSWORD));
    mockMvc
        .perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }

  private String registerAndLogin(String email) throws Exception {
    register(email);
    return login(email);
  }

  private String login(String email) throws Exception {
    MvcResult result =
        mockMvc.perform(loginRequest(email, PASSWORD)).andExpect(status().isOk()).andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("accessToken").asString();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
      String email, String password
  ) throws Exception {
    String body = objectMapper.writeValueAsString(new RegisterPayload(email, password));
    return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private UUID uploadVideo(String token) throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "movie.mp4", "video/mp4", "fake-bytes".getBytes());
    MvcResult result = mockMvc
        .perform(multipart("/videos").file(file).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(json.get("id").asString());
  }

  private record RegisterPayload(String email, String password) {

  }
}
