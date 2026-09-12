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
	 * expiram sozinhas e o evento volta a ser elegível.
	 */
	List<OutboxEvent> claimUnpublished(int limit, Duration lease);

	void markPublished(UUID eventId);

	/** Libera a reserva e conta a tentativa; o evento volta a ser elegível no próximo ciclo. */
	void releaseAfterFailure(UUID eventId);
}
