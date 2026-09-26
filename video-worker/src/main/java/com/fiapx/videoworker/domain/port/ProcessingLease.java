package com.fiapx.videoworker.domain.port;

import java.util.UUID;

public interface ProcessingLease {

  Lease acquire(UUID videoId);

  interface Lease extends AutoCloseable {

    void check();

    @Override
    void close();
  }
}
