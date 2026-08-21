package com.fiapx.videoapi.support;

import com.fiapx.videoapi.domain.port.StorageClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class FakeStorageClient implements StorageClient {

  private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

  @Override
  public void upload(String bucket, String key, InputStream data, long contentLength, String contentType) {
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
      data.transferTo(buffer);
      objects.put(bucket + "/" + key, buffer.toByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public InputStream download(String bucket, String key) {
    byte[] bytes = objects.get(bucket + "/" + key);
    if (bytes == null) {
      throw new IllegalStateException("Objeto não encontrado no fake storage: " + bucket + "/" + key);
    }
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public void delete(String bucket, String key) {
    objects.remove(bucket + "/" + key);
  }

  public boolean exists(String bucket, String key) {
    return objects.containsKey(bucket + "/" + key);
  }
}
