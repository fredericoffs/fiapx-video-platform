-- Preserva a senha já escolhida antes de executar a V8 histórica, sem mudar seu checksum.
DO $$
BEGIN
  IF to_regclass('video_api.users') IS NOT NULL
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'video_api'
                 AND table_name = 'users' AND column_name = 'must_change_password')
     AND NOT EXISTS (SELECT 1 FROM video_api.flyway_schema_history WHERE version = '11' AND success) THEN
    CREATE TABLE IF NOT EXISTS video_api.admin_password_upgrade_backup (id UUID PRIMARY KEY, password_hash TEXT NOT NULL);
    INSERT INTO video_api.admin_password_upgrade_backup
      SELECT id, password_hash FROM video_api.users
      WHERE email = 'admin@fiapx.local' AND NOT must_change_password
        AND password_hash <> '$2a$10$IXpcEF7vts1NOrdY0ipeQuae1TXG.mUIkm9PdUxxGN/K6Jj.piDIG'
      ON CONFLICT (id) DO NOTHING;
  END IF;
END $$;
