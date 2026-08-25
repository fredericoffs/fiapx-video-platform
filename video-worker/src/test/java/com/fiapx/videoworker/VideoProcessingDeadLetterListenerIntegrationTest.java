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
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingEventType;
import com.fiapx.videoworker.infrastructure.messaging.dto.ProcessingResultMessage;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VideoProcessingDeadLetterListenerIntegrationTest {

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private QueueProperties queueProperties;

	@Test
	void messageThatExhaustsRetriesFallsIntoDlqAndPublishesProcessingFailed() {
		UUID videoId = UUID.randomUUID();
		VideoUploadRequestedPayload payload = new VideoUploadRequestedPayload(videoId,
				"raw/" + videoId + "/never-seeded.mp4", "never-seeded.mp4");
		rabbitTemplate.convertAndSend(queueProperties.processing(), objectMapper.writeValueAsString(payload),
				m -> {
					m.getMessageProperties().setCorrelationId("dlq-test-correlation-id");
					return m;
				});

		Message message = receiveContaining(queueProperties.statusUpdates(), videoId.toString());
		assertThat(message).as("mensagem de resultado publicada em %s após DLQ", queueProperties.statusUpdates())
				.isNotNull();

		ProcessingResultMessage result = objectMapper.readValue(new String(message.getBody()),
				ProcessingResultMessage.class);
		assertThat(result.eventType()).isEqualTo(ProcessingEventType.PROCESSING_FAILED);
		assertThat(result.videoId()).isEqualTo(videoId);
		assertThat(result.errorMessage()).isNotBlank();
		// Confirma que o correlation_id sobrevive ao RabbitMQ mover a mensagem pra DLQ.
		assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("dlq-test-correlation-id");
	}

	private Message receiveContaining(String queue, String needle) {
		long deadline = System.currentTimeMillis() + 30_000;
		while (System.currentTimeMillis() < deadline) {
			Message message = rabbitTemplate.receive(queue, 500);
			if (message != null && new String(message.getBody()).contains(needle)) {
				return message;
			}
		}
		return null;
	}
}
