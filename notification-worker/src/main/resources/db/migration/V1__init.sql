CREATE TABLE notification_worker.notification_attempts (
    id             UUID PRIMARY KEY,
    video_id       UUID NOT NULL,
    channel        VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_attempts_lookup ON notification_worker.notification_attempts (video_id, channel, status);
