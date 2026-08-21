package com.fiapx.videoapi;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminIntegrationTest {

  private static final String PASSWORD = "senha-secreta-123";
  private static final String ADMIN_EMAIL = "admin@fiapx.local";
  private static final String ADMIN_PASSWORD = "Admin@123";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void regularUserGetsForbiddenOnAdminEndpoints() throws Exception {
    String token = registerAndLogin(newEmail());

    mockMvc.perform(get("/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/videos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/admin/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminSeesUsersAndVideosFromEveryone() throws Exception {
    String email = newEmail();
    String userToken = registerAndLogin(email);
    UUID videoId = uploadVideo(userToken);
    String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

    MvcResult usersResult = mockMvc
        .perform(get("/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode users = objectMapper.readTree(usersResult.getResponse().getContentAsString());
    assertThat(users.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);

    MvcResult videosResult = mockMvc
        .perform(get("/admin/videos").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode videos = objectMapper.readTree(videosResult.getResponse().getContentAsString());
    assertThat(videos.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
    JsonNode uploadedVideo = findVideoById(videos.get("items"), videoId);
    assertThat(uploadedVideo).isNotNull();
    assertThat(uploadedVideo.get("ownerEmail").asString()).isEqualTo(email);
  }

  private JsonNode findVideoById(JsonNode items, UUID videoId) {
    for (JsonNode item : items) {
      if (UUID.fromString(item.get("id").asString()).equals(videoId)) {
        return item;
      }
    }
    return null;
  }

  @Test
  void adminDeletesUserAndCascadesTheirVideos() throws Exception {
    String email = newEmail();
    String userToken = registerAndLogin(email);
    UUID videoId = uploadVideo(userToken);
    String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

    UUID userId = extractUserId(userToken);
    mockMvc.perform(delete("/admin/users/" + userId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/videos/" + videoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  private String newEmail() {
    return "user-" + UUID.randomUUID() + "@fiapx.com";
  }

  private String registerAndLogin(String email) throws Exception {
    String body = objectMapper.writeValueAsString(new RegisterPayload(email, PASSWORD));
    mockMvc
        .perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    return login(email, PASSWORD);
  }

  private String login(String email, String password) throws Exception {
    String body = objectMapper.writeValueAsString(new RegisterPayload(email, password));
    MvcResult result = mockMvc
        .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("accessToken").asString();
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

  private UUID extractUserId(String token) {
    String payload = token.split("\\.")[1];
    String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload));
    JsonNode json = objectMapper.readTree(decoded);
    return UUID.fromString(json.get("sub").asString());
  }

  private record RegisterPayload(String email, String password) {

  }
}
