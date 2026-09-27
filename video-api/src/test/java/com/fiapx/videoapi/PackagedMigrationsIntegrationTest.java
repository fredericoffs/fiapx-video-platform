package com.fiapx.videoapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fiapx.videoapi.infrastructure.storage.PersistentStorageCleanup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PackagedMigrationsIntegrationTest {

  @Container
  static final PostgreSQLContainer DB = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
  @TempDir
  Path dir;

  private Flyway flyway(String target) {
    return Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
        .schemas("video_api").defaultSchema("video_api").cleanDisabled(false)
        .locations("filesystem:" + dir).target(target).load();
  }

  private void copyPackage(boolean callback) throws Exception {
    String manifest = Files.readString(Path.of("../k8s/apps/base/video-api/kustomization.yaml"));

    try (var stream = Files.list(Path.of("src/main/resources/db/migration"))) {
      List<Path> sources = stream.toList();
      for (var source : sources) {
        assertThat(manifest).contains("db/migration/" + source.getFileName());
        if (callback || !source.getFileName().toString().equals("beforeMigrate.sql")) {
          Files.copy(source, dir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  @Test
  void packageUpgradesV7PreservingChosenPassword() throws Exception {
    copyPackage(true);
    flyway("7").clean();
    flyway("7").migrate();
    try (var conn = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var sql = conn.createStatement()) {
      sql.execute("UPDATE video_api.users SET password_hash = 'chosen-hash', must_change_password = false WHERE email = 'admin@fiapx.local'");
      flyway("latest").migrate();
      try (var rows = sql.executeQuery(
          "SELECT password_hash, must_change_password, tokens_valid_after FROM video_api.users WHERE email = 'admin@fiapx.local'")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("chosen-hash");
        assertThat(rows.getBoolean(2)).isFalse();
        assertThat(rows.getTimestamp(3)).isNotNull();
      }
      sql.executeQuery("SELECT locked_by FROM video_api.outbox_events").close();
      sql.executeQuery("SELECT * FROM video_api.storage_cleanup").close();
    }
  }

  @Test
  void alreadyAffectedV8AccountCanBeResetInsteadOfRemainingLockedOut() throws Exception {
    copyPackage(false);
    flyway("7").clean();
    flyway("7").migrate();
    try (var conn = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()); var sql = conn.createStatement()) {
      sql.execute("UPDATE video_api.users SET password_hash = 'chosen-hash', must_change_password = false WHERE email = 'admin@fiapx.local'");
      flyway("8").migrate();
      copyPackage(true);
      flyway("latest").migrate();

      try (var rows = sql.executeQuery("SELECT must_change_password FROM video_api.users WHERE email = 'admin@fiapx.local'")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getBoolean(1)).isTrue();
      }
    }
  }

  @Test
  void cleanupSurvivesStorageFailureAndDoesNotEscapeTransactionRollback() throws Exception {
    copyPackage(true);
    flyway("latest").clean();
    flyway("latest").migrate();
    var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    var jdbc = new JdbcTemplate(dataSource);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    var storage = mock(com.fiapx.videoapi.domain.port.StorageClient.class);
    var cleanup = new PersistentStorageCleanup(jdbc, storage);

    tx.executeWithoutResult(status -> {
      cleanup.delete("raw", "rollback");
      status.setRollbackOnly();
    });

    assertThat(jdbc.queryForObject("SELECT count(*) FROM video_api.storage_cleanup", Integer.class)).isZero();
    cleanup.delete("raw", "retry");
    cleanup.delete("raw", "success");
    doThrow(new IllegalStateException("S3 indisponível")).when(storage).delete("raw", "retry");

    tx.executeWithoutResult(status -> cleanup.cleanupPending());

    assertThat(jdbc.queryForObject("SELECT attempts FROM video_api.storage_cleanup WHERE object_key = 'retry'", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM video_api.storage_cleanup", Integer.class)).isEqualTo(1);

    doNothing().when(storage).delete("raw", "retry");

    jdbc.update("UPDATE video_api.storage_cleanup SET retry_at = now()");
    tx.executeWithoutResult(status -> cleanup.cleanupPending());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM video_api.storage_cleanup", Integer.class)).isZero();
  }


  @Test
  void scheduledOrphanCleanupWaitsForItsDelayAndCanBeCancelledOnlyOnce() throws Exception {
    copyPackage(true);
    flyway("latest").clean();
    flyway("latest").migrate();
    var dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    var jdbc = new JdbcTemplate(dataSource);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    var storage = mock(com.fiapx.videoapi.domain.port.StorageClient.class);
    var cleanup = new PersistentStorageCleanup(jdbc, storage);

    cleanup.schedule("raw", "reserved", java.time.Duration.ofHours(1));
    cleanup.schedule("raw", "orphan", java.time.Duration.ofHours(1));

    // Reserva ainda no prazo: a limpeza não toca no objeto que está sendo enviado.
    tx.executeWithoutResult(status -> cleanup.cleanupPending());
    verify(storage, never()).delete(anyString(), anyString());
    assertThat(jdbc.queryForObject(
        "SELECT retry_at > now() + interval '59 minutes' FROM video_api.storage_cleanup WHERE object_key = 'reserved'",
        Boolean.class)).isTrue();

    assertThat(cleanup.cancel("raw", "reserved")).isTrue();
    assertThat(cleanup.cancel("raw", "reserved")).isFalse();

    // Upload que nunca chegou à fase 3: vencida a reserva, o objeto órfão é apagado.
    jdbc.update("UPDATE video_api.storage_cleanup SET retry_at = now()");
    tx.executeWithoutResult(status -> cleanup.cleanupPending());
    verify(storage).delete("raw", "orphan");
    verify(storage, never()).delete("raw", "reserved");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM video_api.storage_cleanup", Integer.class)).isZero();
  }
}
