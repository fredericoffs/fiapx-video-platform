package com.fiapx.videoworker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.fiapx.videoworker.application.dto.VideoUploadRequestedPayload;
import com.fiapx.videoworker.infrastructure.config.QueueProperties;
import com.fiapx.videoworker.infrastructure.config.StorageProperties;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;
import com.fiapx.videoworker.support.FakeStorageClient;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoProcessingListenerIntegrationTest {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private QueueProperties queueProperties;

	@Autowired
	private StorageProperties storageProperties;

	@Autowired
	private FakeStorageClient fakeStorageClient;

	@Test
	void processesMessageAndPublishesProcessingCompleted() {
		UUID videoId = UUID.randomUUID();
		String storageKey = "raw/" + videoId + "/movie.mp4";
		fakeStorageClient.seed(storageProperties.bucketRaw(), storageKey, "fake-video-bytes".getBytes());

		VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId, storageKey, "movie.mp4");
		rabbitTemplate.convertAndSend(queueProperties.processing(), objectMapper.writeValueAsString(payload));

		Message message = receiveContaining(queueProperties.statusUpdates(), videoId.toString());
		assertThat(message).as("mensagem de resultado publicada em %s", queueProperties.statusUpdates()).isNotNull();

		ProcessingResultMessage result = objectMapper.readValue(new String(message.getBody()),
				ProcessingResultMessage.class);
		assertThat(result.eventType()).isEqualTo(ProcessingEventType.PROCESSING_COMPLETED);
		assertThat(result.videoId()).isEqualTo(videoId);
		assertThat(result.zipStorageKey()).isEqualTo("processed/" + videoId + "/" + videoId + ".zip");
	}

	private Message receiveContaining(String queue, String needle) {
		long deadline = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < deadline) {
			Message message = rabbitTemplate.receive(queue, 500);
			if (message != null && new String(message.getBody()).contains(needle)) {
				return message;
			}
		}
		return null;
	}
}
