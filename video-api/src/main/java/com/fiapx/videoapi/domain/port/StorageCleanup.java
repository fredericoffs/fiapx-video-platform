package com.fiapx.videoapi.domain.port;

import java.time.Duration;

public interface StorageCleanup {

  void delete(String bucket, String key);

  /**
   * Agenda a remoção do objeto para daqui a {@code delay}, antes de ele existir: se o fluxo
   * que vai criá-lo não chegar a {@link #cancel}, o objeto órfão é apagado sozinho.
   */
  void schedule(String bucket, String key, Duration delay);

  /** Cancela um agendamento; false se ele não existia mais (já consumido pela limpeza). */
  boolean cancel(String bucket, String key);
}
