-- Idempotência garantida pelo banco: no máximo um envio bem-sucedido por vídeo e canal,
-- mesmo com reentrega da fila ou duas réplicas processando a mesma mensagem.
CREATE UNIQUE INDEX uq_notification_attempts_sent
    ON notification_worker.notification_attempts (video_id, channel)
    WHERE status = 'SENT';
