CREATE TABLE video_api.videos (
    id                 UUID PRIMARY KEY,
    original_filename  VARCHAR(255) NOT NULL,
    storage_key        VARCHAR(500) NOT NULL,
    zip_storage_key    VARCHAR(500),
    status             VARCHAR(20)  NOT NULL,
    error_message      TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_videos_status ON video_api.videos (status);

CREATE TABLE video_api.outbox_events (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB NOT NULL,
    published     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_events_unpublished ON video_api.outbox_events (created_at) WHERE published = FALSE;
