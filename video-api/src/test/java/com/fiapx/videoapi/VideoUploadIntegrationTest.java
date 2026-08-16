package com.fiapx.videoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fiapx.videoapi.infrastructure.persistence.entity.OutboxEventEntity;
import com.fiapx.videoapi.infrastructure.persistence.entity.VideoEntity;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fiapx.videoapi.infrastructure.persistence.repository.SpringDataVideoRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VideoUploadIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SpringDataVideoRepository videoRepository;

	@Autowired
	private SpringDataOutboxEventRepository springDataOutboxEventRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void uploadPersistsQueuedVideoAndUnpublishedOutboxEvent() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "movie.mp4", "video/mp4", "fake-bytes".getBytes());

		MvcResult result = mockMvc.perform(multipart("/videos").file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andReturn();

		JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
		UUID videoId = UUID.fromString(json.get("id").asString());

		Optional<VideoEntity> savedVideo = videoRepository.findById(videoId);
		assertThat(savedVideo).isPresent();
		assertThat(savedVideo.get().getOriginalFilename()).isEqualTo("movie.mp4");
		assertThat(savedVideo.get().getStatus().name()).isEqualTo("QUEUED");

		List<OutboxEventEntity> events = springDataOutboxEventRepository.findAll().stream()
				.filter(event -> event.getAggregateId().equals(videoId))
				.toList();
		assertThat(events).hasSize(1);
		assertThat(events.get(0).getEventType()).isEqualTo("VideoUploadRequested");
		assertThat(events.get(0).isPublished()).isFalse();
	}
}
