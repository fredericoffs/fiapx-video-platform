package com.fiapx.videoapi.domain.port;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fiapx.videoapi.domain.model.OutboxEvent;

public interface OutboxEventRepository {

	void save(OutboxEvent event);

	/**
	 * Reserva (lease) um lote de eventos pendentes para esta instância: enquanto a reserva não
	 * expira, nenhuma outra réplica recebe os mesmos eventos. Reservas de instâncias que caíram
	 * expiram sozinhas e o evento volta a ser elegível. Cada evento retornado carrega o token
	 * desta reivindicação (OutboxEvent#getLockToken) — markPublished/releaseAfterFailure exigem
	 * o token de volta pra confirmar que quem está concluindo ainda é o dono atual da reserva.
	 */
	List<OutboxEvent> claimUnpublished(int limit, Duration lease);

	/** No-op se lockToken não corresponder à reserva atual (reivindicação antiga concluindo tarde). */
	void markPublished(UUID eventId, UUID lockToken);

	/**
	 * Libera a reserva e conta a tentativa; o evento volta a ser elegível no próximo ciclo.
	 * No-op se lockToken não corresponder à reserva atual (reivindicação antiga concluindo tarde).
	 */
	void releaseAfterFailure(UUID eventId, UUID lockToken);
}
