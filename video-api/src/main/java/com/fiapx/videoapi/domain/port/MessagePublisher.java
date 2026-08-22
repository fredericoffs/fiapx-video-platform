package com.fiapx.videoapi.domain.port;

public interface MessagePublisher {

	void publish(String queueName, String payloadJson, String correlationId);
}
