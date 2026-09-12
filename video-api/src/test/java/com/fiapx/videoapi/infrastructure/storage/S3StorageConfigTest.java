package com.fiapx.videoapi.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

class S3StorageConfigTest {

  @Test
  void localProfileUsesEndpointStaticKeysAndPathStyle() {
    StorageProperties minio = new StorageProperties("http://localhost:9000", "minioadmin", "minioadmin",
        "videos-raw", "videos-processed", "us-east-1", true);

    try (S3Client client = S3StorageConfig.configure(S3Client.builder(), minio).build()) {
      assertThat(client.serviceClientConfiguration().endpointOverride()).contains(URI.create("http://localhost:9000"));
      assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
    }
    assertThat(S3StorageConfig.credentialsProvider(minio)).isInstanceOf(StaticCredentialsProvider.class);
    assertThat(minio.pathStyle()).isTrue();
  }

  @Test
  void awsProfileUsesRegionalEndpointAndDefaultCredentialChain() {
    StorageProperties aws = new StorageProperties("", "", "", "fiapx-videos-raw-123", "fiapx-videos-processed-123",
        "us-east-1", false);

    try (S3Client client = S3StorageConfig.configure(S3Client.builder(), aws).build()) {
      assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
      assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
    }
    assertThat(S3StorageConfig.credentialsProvider(aws)).isInstanceOf(DefaultCredentialsProvider.class);
    assertThat(aws.hasEndpointOverride()).isFalse();
    assertThat(aws.hasStaticCredentials()).isFalse();
  }
}
