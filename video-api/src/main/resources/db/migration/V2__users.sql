CREATE TABLE video_api.users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_users_email ON video_api.users (email);

ALTER TABLE video_api.videos ADD COLUMN user_id UUID REFERENCES video_api.users (id);

CREATE INDEX idx_videos_user_id ON video_api.videos (user_id);
