-- Item 8 da revisão crítica: o padrão anterior era "consultar se já enviou -> enviar -> registrar
-- sucesso". Duas execuções concorrentes (reentrega da fila, ou duas réplicas) passavam pela
-- checagem antes de qualquer uma registrar SENT, então as duas chamavam o canal de verdade --
-- o índice único só impedia duas LINHAS de sucesso, nunca os dois envios reais. Agora a
-- reivindicação (INSERT status=SENDING) acontece ANTES do canal ser chamado, e o índice único
-- cobre SENDING e SENT: a segunda execução concorrente falha em reivindicar a linha e nem chega
-- a chamar o canal.
DROP INDEX notification_worker.uq_notification_attempts_sent;

CREATE UNIQUE INDEX uq_notification_attempts_claim
    ON notification_worker.notification_attempts (video_id, channel)
    WHERE status IN ('SENDING', 'SENT');
