package com.fiapx.videoapi.domain.port;

import com.fiapx.videoapi.domain.model.OutboundMessage;

/** Publicação confirmada: só retorna quando o broker aceitou a mensagem; caso contrário lança exceção. */
public interface MessagePublisher {

	void publish(String queueName, OutboundMessage message);
}
