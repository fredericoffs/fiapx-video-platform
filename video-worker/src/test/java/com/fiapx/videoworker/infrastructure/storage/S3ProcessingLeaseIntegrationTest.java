package com.fiapx.videoworker.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiapx.videoworker.infrastructure.config.StorageProperties;
import com.fiapx.videoworker.infrastructure.messaging.sqs.SqsTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

class S3ProcessingLeaseIntegrationTest {

  static S3Client s3;
  static S3ProcessingLease leases;

  @BeforeAll
  static void setup() {
    var local = SqsTestSupport.LOCALSTACK;
    var props = new StorageProperties(local.getEndpoint().toString(), local.getAccessKey(), local.getSecretKey(),
        "lease-raw", "lease-results", local.getRegion(), true);
    s3 = S3StorageConfig.configure(S3Client.builder(), props).build();
    s3.createBucket(b -> b.bucket("lease-results"));
    leases = new S3ProcessingLease(s3, props);
  }

  @Test
  void onlyOneWorkerCanHoldLeaseAndReleaseAllowsRetry() {
    UUID id = UUID.randomUUID();

    try (var first = leases.acquire(id)) {
      first.check();
      assertThatThrownBy(() -> {
        try (var duplicate = leases.acquire(id)) {
          duplicate.check();
        }
      }).isInstanceOf(IllegalStateException.class);
    }

    try (var next = leases.acquire(id)) {
      next.check();
    }
  }

  @Test
  void expiredLeaseIsRecoveredAndOldOwnerCannotReleaseNewOwner() {
    UUID id = UUID.randomUUID();
    var old = leases.acquire(id);
    s3.putObject(b -> b.bucket("lease-results").key("leases/" + id), RequestBody.fromString("1970-01-01T00:00:00Z\nexpired"));

    try (var current = leases.acquire(id)) {
      assertThatThrownBy(old::check).isInstanceOf(RuntimeException.class);
      assertThatThrownBy(old::check).isInstanceOf(IllegalStateException.class);
      old.close();
      current.check();
      assertThatThrownBy(() -> {
        try (var newLease = leases.acquire(id)) {
          newLease.check();
        }
      }).isInstanceOf(IllegalStateException.class);
    }
  }
}
