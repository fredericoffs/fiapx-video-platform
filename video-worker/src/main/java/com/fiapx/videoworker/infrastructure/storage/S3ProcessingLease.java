package com.fiapx.videoworker.infrastructure.storage;

import com.fiapx.videoworker.domain.port.ProcessingLease;
import com.fiapx.videoworker.infrastructure.config.StorageProperties;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Reserva por vídeo com compare-and-swap no S3. Expira após queda do pod.
 */
@Component
public class S3ProcessingLease implements ProcessingLease {

  private final S3Client s3;
  private final String bucket;

  public S3ProcessingLease(S3Client s3, StorageProperties properties) {
    this.s3 = s3;
    this.bucket = properties.bucketProcessed();
  }

  public Lease acquire(UUID videoId) {
    String key = "leases/" + videoId;
    String etag = null;
    try {
      var existing = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());

      if (Instant.parse(existing.asUtf8String().split("\n")[0]).isAfter(Instant.now())) {
        throw new IllegalStateException("Vídeo já reservado por outro worker");
      }
      etag = existing.response().eTag();

    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
    }

    return new Lease(key, write(key, etag, Instant.now().plusSeconds(90)));
  }

  private String write(String key, String etag, Instant until) {
    var request = PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .overrideConfiguration(c -> c.apiCallTimeout(java.time.Duration.ofSeconds(10)));
    if (etag == null) {
      request.ifNoneMatch("*");
    } else {
      request.ifMatch(etag);
    }
    return s3.putObject(request.build(), RequestBody.fromString(until + "\n" + UUID.randomUUID())).eTag();
  }

  public final class Lease implements ProcessingLease.Lease {

    private final String key;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private String etag;
    private boolean lost;

    Lease(String key, String etag) {
      this.key = key;
      this.etag = etag;
      executor.scheduleAtFixedRate(() -> {
        try {
          check();
        } catch (RuntimeException ignored) {
          /* check marca a reserva perdida. */
        }
      }, 20, 20, java.util.concurrent.TimeUnit.SECONDS);
    }

    public synchronized void check() {
      if (lost) {
        throw new IllegalStateException("Reserva de processamento perdida; aguardar reentrega");
      }
      try {
        etag = write(key, etag, Instant.now().plusSeconds(90));
      } catch (RuntimeException failure) {
        lost = true;
        throw failure;
      }
    }

    @Override
    public synchronized void close() {
      executor.shutdownNow();
      if (!lost) {
        try {
          write(key, etag, Instant.EPOCH);
        } catch (RuntimeException ignored) {
          /* A reserva expira sem apagar a de outro worker. */
        }
      }
    }
  }
}
