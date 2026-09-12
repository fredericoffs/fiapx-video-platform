-- Coordenação entre réplicas do video-api: cada lote pendente é reservado (lease curto)
-- com FOR UPDATE SKIP LOCKED, então duas instâncias nunca publicam o mesmo evento ao
-- mesmo tempo, e uma reserva de instância que caiu expira sozinha.
ALTER TABLE video_api.outbox_events ADD COLUMN locked_until TIMESTAMPTZ;
ALTER TABLE video_api.outbox_events ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;

DROP INDEX IF EXISTS video_api.idx_outbox_events_unpublished;
CREATE INDEX idx_outbox_events_unpublished
    ON video_api.outbox_events (created_at)
    WHERE published = FALSE;
