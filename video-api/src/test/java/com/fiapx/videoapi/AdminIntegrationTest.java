package com.fiapx.videoapi;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V8 deixa o admin semeado com um hash placeholder inutilizável (ver comentário da
 * migration) — só {@link com.fiapx.videoapi.application.usecase.SeedAdminPasswordUseCase},
 * lendo {@code fiapx.admin.seed-password}, aplica uma senha utilizável. Defino esse valor
 * aqui, igual ao {@code ADMIN_PASSWORD} usado nos logins abaixo, pra não depender de uma
 * variável de ambiente externa (SSM em produção) só pra este teste subir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = "fiapx.admin.seed-password=Admin@123")
class AdminIntegrationTest extends AbstractSqsIntegrationTest {

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

  @Test
  void seededAdminMustChangePasswordOnFirstLoginThenFlagClears() throws Exception {
    JsonNode firstLogin = loginResponse(ADMIN_EMAIL, ADMIN_PASSWORD);
    assertThat(firstLogin.get("mustChangePassword").asBoolean()).isTrue();
    String adminToken = firstLogin.get("accessToken").asString();
    String newPassword = "NovaSenhaAdmin@123";

    try {
      String changeBody = objectMapper.writeValueAsString(new ChangePasswordPayload(ADMIN_PASSWORD, newPassword));
      mockMvc
          .perform(
              put("/users/me/password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                  .content(changeBody)
          )
          .andExpect(status().isNoContent());

      JsonNode secondLogin = loginResponse(ADMIN_EMAIL, newPassword);
      assertThat(secondLogin.get("mustChangePassword").asBoolean()).isFalse();
    } finally {
      String restoreToken = login(ADMIN_EMAIL, newPassword);
      String restoreBody = objectMapper.writeValueAsString(new ChangePasswordPayload(newPassword, ADMIN_PASSWORD));
      mockMvc
          .perform(
              put("/users/me/password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + restoreToken)
                  .content(restoreBody)
          )
          .andExpect(status().isNoContent());
    }
  }

  @Test
  void changePasswordRejectsWrongCurrentPassword() throws Exception {
    String token = registerAndLogin(newEmail());
    String body = objectMapper.writeValueAsString(new ChangePasswordPayload("senha-errada", "outra-senha-123"));

    mockMvc
        .perform(
            put("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(body)
        )
        .andExpect(status().isUnauthorized());
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
  void adminFiltersUsersByEmailSubstring() throws Exception {
    String uniqueMarker = "marker-" + UUID.randomUUID();
    String email = uniqueMarker + "@fiapx.com";
    registerAndLogin(email);
    String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

    MvcResult result = mockMvc
        .perform(
            get("/admin/users")
                .param("email", uniqueMarker)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        )
        .andExpect(status().isOk())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(json.get("totalElements").asLong()).isEqualTo(1);
    assertThat(json.get("items").get(0).get("email").asString()).isEqualTo(email);
  }

  @Test
  void adminFiltersVideosByFilenameSubstring() throws Exception {
    String userToken = registerAndLogin(newEmail());
    String uniqueMarker = "marker-" + UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile("file", uniqueMarker + ".mp4", "video/mp4", "fake".getBytes());
    mockMvc.perform(multipart("/videos").file(file).header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
        .andExpect(status().isCreated());
    String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

    MvcResult result = mockMvc
        .perform(
            get("/admin/videos")
                .param("filename", uniqueMarker)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        )
        .andExpect(status().isOk())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(json.get("totalElements").asLong()).isEqualTo(1);
    assertThat(json.get("items").get(0).get("originalFilename").asString()).isEqualTo(uniqueMarker + ".mp4");
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
    return loginResponse(email, password).get("accessToken").asString();
  }

  private JsonNode loginResponse(String email, String password) throws Exception {
    String body = objectMapper.writeValueAsString(new RegisterPayload(email, password));
    MvcResult result = mockMvc
        .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
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

  private record ChangePasswordPayload(String currentPassword, String newPassword) {

  }
}
