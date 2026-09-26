CREATE TABLE video_api.storage_cleanup (
 id UUID PRIMARY KEY, bucket TEXT NOT NULL, object_key TEXT NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0, retry_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(bucket, object_key)
);
