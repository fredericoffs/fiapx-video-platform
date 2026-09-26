-- A reserva (lease) existente identifica só o prazo (locked_until), não quem a detém.
-- Uma reivindicação lenta cujo lease expira antes de terminar de publicar podia, ao concluir
-- (markPublished/releaseAfterFailure), sobrescrever a reserva que outra réplica já tinha
-- assumido nesse meio-tempo: markPublished tardio confirmava um evento que a réplica nova
-- ainda estava processando, ou releaseAfterFailure tardio liberava a reserva da réplica nova
-- e contava uma tentativa que não era dela. locked_by amarra completar/liberar ao token
-- gerado no momento exato da reivindicação, tornando essas conclusões tardias um no-op.
ALTER TABLE video_api.outbox_events ADD COLUMN locked_by UUID;
