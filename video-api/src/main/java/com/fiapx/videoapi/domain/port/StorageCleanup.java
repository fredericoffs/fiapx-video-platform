package com.fiapx.videoapi.domain.port;

public interface StorageCleanup {

  void delete(String bucket, String key);
}
