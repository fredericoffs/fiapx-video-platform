package com.fiapx.videoapi.infrastructure.storage;

import com.fiapx.videoapi.domain.port.StorageCleanup;
import com.fiapx.videoapi.domain.port.StorageClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersistentStorageCleanup implements StorageCleanup {

  private final JdbcTemplate jdbc;
  private final StorageClient storage;

  public PersistentStorageCleanup(JdbcTemplate jdbc, StorageClient storage) {
    this.jdbc = jdbc;
    this.storage = storage;
  }

  @Override
  public void delete(String bucket, String key) {
    jdbc.update("INSERT INTO video_api.storage_cleanup(id, bucket, object_key) VALUES (?, ?, ?) ON CONFLICT (bucket, object_key) DO NOTHING",
        UUID.randomUUID(), bucket, key);
  }

  @Override
  public void schedule(String bucket, String key, Duration delay) {
    jdbc.update("INSERT INTO video_api.storage_cleanup(id, bucket, object_key, retry_at) "
            + "VALUES (?, ?, ?, now() + make_interval(secs => ?)) ON CONFLICT (bucket, object_key) DO NOTHING",
        UUID.randomUUID(), bucket, key, delay.toSeconds());
  }

  // Se a limpeza estiver com a linha travada (FOR UPDATE SKIP LOCKED), o DELETE espera ela
  // terminar e devolve 0 — o chamador fica sabendo que o objeto pode já ter sido apagado.
  @Override
  public boolean cancel(String bucket, String key) {
    return jdbc.update("DELETE FROM video_api.storage_cleanup WHERE bucket = ? AND object_key = ?", bucket, key) == 1;
  }

  @Scheduled(fixedDelayString = "${fiapx.storage.cleanup-interval-ms:10000}")
  @Transactional
  public void cleanupPending() {
    var entries = jdbc.queryForList(
        "SELECT id, bucket, object_key FROM video_api.storage_cleanup WHERE retry_at <= now() ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED");
    for (var entry : entries) {
      try {
        storage.delete((String) entry.get("bucket"), (String) entry.get("object_key"));
      } catch (RuntimeException failure) {
        jdbc.update("UPDATE video_api.storage_cleanup SET attempts = attempts + 1, retry_at = now() + interval '1 minute' WHERE id = ?",
            entry.get("id"));
        continue;
      }
      jdbc.update("DELETE FROM video_api.storage_cleanup WHERE id = ?", entry.get("id"));
    }
  }
}
