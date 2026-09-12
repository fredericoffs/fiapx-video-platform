package com.fiapx.videoapi.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** O adapter real contra um S3 real (LocalStack), pelo mesmo caminho de configuração usado com MinIO e com a AWS. */
@Testcontainers
class S3StorageClientIntegrationTest {

  @Container
  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.0"));

  static S3StorageClient client;

  @BeforeAll
  static void setUp() {
    StorageProperties properties = new StorageProperties(LOCALSTACK.getEndpoint().toString(),
        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey(), "videos-raw", "videos-processed",
        LOCALSTACK.getRegion(), true);
    S3Client s3 = S3StorageConfig.configure(S3Client.builder(), properties).build();
    s3.createBucket(CreateBucketRequest.builder().bucket("videos-raw").build());
    client = new S3StorageClient(s3);
  }

  @Test
  void uploadsDownloadsAndDeletesAnObject() throws Exception {
    byte[] content = "conteudo-do-video".getBytes(StandardCharsets.UTF_8);

    client.upload("videos-raw", "raw/v1/source.mp4", new ByteArrayInputStream(content), content.length, "video/mp4");

    try (InputStream in = client.download("videos-raw", "raw/v1/source.mp4")) {
      assertThat(in.readAllBytes()).isEqualTo(content);
    }

    client.delete("videos-raw", "raw/v1/source.mp4");

    assertThatThrownBy(() -> client.download("videos-raw", "raw/v1/source.mp4"))
        .isInstanceOf(NoSuchKeyException.class);
  }

  @Test
  void objectsAreIsolatedByKey() throws Exception {
    client.upload("videos-raw", "raw/a/source.mp4", new ByteArrayInputStream("A".getBytes()), 1, "video/mp4");
    client.upload("videos-raw", "raw/b/source.mp4", new ByteArrayInputStream("B".getBytes()), 1, "video/mp4");

    try (InputStream a = client.download("videos-raw", "raw/a/source.mp4");
        InputStream b = client.download("videos-raw", "raw/b/source.mp4")) {
      assertThat(new String(a.readAllBytes())).isEqualTo("A");
      assertThat(new String(b.readAllBytes())).isEqualTo("B");
    }
  }
}
