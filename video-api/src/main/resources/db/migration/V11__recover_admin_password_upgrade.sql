-- Atualização V7 -> atual: restaura a senha preservada pelo callback antes da V8.
CREATE TABLE IF NOT EXISTS video_api.admin_password_upgrade_backup (id UUID PRIMARY KEY, password_hash TEXT NOT NULL);
UPDATE video_api.users u SET password_hash = b.password_hash
FROM video_api.admin_password_upgrade_backup b WHERE u.id = b.id;
DROP TABLE video_api.admin_password_upgrade_backup;
-- Se V8 já foi aplicada sem backup, não há como recuperar o hash anterior: libera reset seguro.
UPDATE video_api.users SET must_change_password = TRUE, tokens_valid_after = now()
WHERE email = 'admin@fiapx.local' AND NOT must_change_password
  AND password_hash = '$2a$10$IXpcEF7vts1NOrdY0ipeQuae1TXG.mUIkm9PdUxxGN/K6Jj.piDIG';
