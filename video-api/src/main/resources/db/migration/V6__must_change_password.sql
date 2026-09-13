ALTER TABLE video_api.users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- O admin seed (V3) usa uma senha fixa e documentada — força a troca no primeiro login.
UPDATE video_api.users SET must_change_password = TRUE WHERE email = 'admin@fiapx.local';
