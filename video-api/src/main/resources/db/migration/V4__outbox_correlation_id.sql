-- Nullable: eventos publicados antes desta migration não têm correlation_id, e o
-- valor só existe de fato a partir do increment 3.6 da Sprint 7 (quando o listener de
-- status do video-api também passa a propagar o header AMQP pro MDC).
ALTER TABLE video_api.outbox_events ADD COLUMN correlation_id VARCHAR(64);
