package com.fiapx.videoapi;

import com.fiapx.videoapi.domain.model.PageResult;
import com.fiapx.videoapi.domain.model.Video;
import com.fiapx.videoapi.domain.model.VideoFilter;
import com.fiapx.videoapi.domain.model.VideoSort;
import com.fiapx.videoapi.domain.model.VideoSortField;
import com.fiapx.videoapi.domain.model.VideoStatus;
import com.fiapx.videoapi.domain.port.VideoRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtro por período e ordenação por data, nome e tamanho contra o Postgres real — a
 * ordenação case-insensitive, o NULLS LAST e o escape do LIKE dependem do SQL gerado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class VideoListFilterAndSortIntegrationTest extends AbstractSqsIntegrationTest {

  private static final Instant BASE = Instant.parse("2026-01-10T12:00:00Z");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private VideoRepository videoRepository;

  private String token;
  private UUID userId;

  @BeforeEach
  void setUp() throws Exception {
    String email = "sort-" + UUID.randomUUID() + "@fiapx.com";
    String body = objectMapper.writeValueAsString(new Credentials(email, "senha-secreta-123"));
    mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    String response = mockMvc
        .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    token = objectMapper.readTree(response).get("accessToken").asString();
    String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    userId = UUID.fromString(objectMapper.readTree(payload).get("sub").asString());

    save("b.mp4", 300L, BASE);
    save("A.mp4", null, BASE.plus(1, ChronoUnit.HOURS));
    save("c.mp4", 100L, BASE.plus(2, ChronoUnit.HOURS));
  }

  @Test
  void defaultsToNewestFirst() throws Exception {
    assertThat(names(list())).containsExactly("c.mp4", "A.mp4", "b.mp4");
  }

  @Test
  void sortsByFilenameIgnoringCase() throws Exception {
    assertThat(names(list().param("sortBy", "FILENAME").param("direction", "ASC")))
        .containsExactly("A.mp4", "b.mp4", "c.mp4");
  }

  @Test
  void sortsByFileSizeKeepingUnknownSizesLastInBothDirections() throws Exception {
    assertThat(names(list().param("sortBy", "FILE_SIZE").param("direction", "DESC")))
        .containsExactly("b.mp4", "c.mp4", "A.mp4");
    assertThat(names(list().param("sortBy", "FILE_SIZE").param("direction", "ASC")))
        .containsExactly("c.mp4", "b.mp4", "A.mp4");
  }

  @Test
  void filtersByInclusiveCreatedAtRange() throws Exception {
    String hour = BASE.plus(1, ChronoUnit.HOURS).toString();
    assertThat(names(list().param("createdFrom", hour).param("createdTo", hour))).containsExactly("A.mp4");
    assertThat(names(list().param("createdFrom", hour))).containsExactly("c.mp4", "A.mp4");
  }

  @Test
  void filenameFilterTreatsLikeWildcardsAsLiterals() {
    String marker = UUID.randomUUID().toString();
    save(marker + "-50%_off.mp4", 1L, BASE);
    save(marker + "-50xyoff.mp4", 1L, BASE);

    PageResult<Video> result = videoRepository.findAll(
        new VideoFilter(null, marker + "-50%_", null, null), VideoSort.NEWEST_FIRST, 0, 10);

    assertThat(result.items()).extracting(Video::getOriginalFilename).containsExactly(marker + "-50%_off.mp4");
    PageResult<Video> sortedByName = videoRepository.findAll(
        new VideoFilter(null, marker, null, null), new VideoSort(VideoSortField.FILENAME, true), 0, 10);
    assertThat(sortedByName.items()).extracting(Video::getOriginalFilename)
        .containsExactly(marker + "-50%_off.mp4", marker + "-50xyoff.mp4");
  }

  private void save(String filename, Long size, Instant createdAt) {
    videoRepository.save(new Video(
        UUID.randomUUID(), userId, filename, "raw/" + UUID.randomUUID(), size, null,
        VideoStatus.COMPLETED, null, createdAt, createdAt, null));
  }

  private MockHttpServletRequestBuilder list() {
    return get("/videos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private List<String> names(MockHttpServletRequestBuilder request) throws Exception {
    String response = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
        .getContentAsString();
    List<String> names = new ArrayList<>();
    for (JsonNode item : objectMapper.readTree(response).get("items")) {
      names.add(item.get("originalFilename").asString());
    }
    return names;
  }

  private record Credentials(String email, String password) {

  }
}
