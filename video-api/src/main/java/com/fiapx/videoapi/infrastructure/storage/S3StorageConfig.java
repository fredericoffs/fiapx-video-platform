package com.fiapx.videoapi.infrastructure.storage;

import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class S3StorageConfig {

  @Bean
  public S3Client s3Client(StorageProperties storageProperties) {
    return configure(S3Client.builder(), storageProperties).build();
  }

  // Mesmo adapter para MinIO (endpoint + chaves + path-style) e S3 real (nada disso):
  // a diferença fica toda nas propriedades, não em código de negócio.
  static S3ClientBuilder configure(S3ClientBuilder builder, StorageProperties properties) {
    builder.region(Region.of(properties.region()))
        .credentialsProvider(credentialsProvider(properties))
        .forcePathStyle(properties.pathStyle());
    if (properties.hasEndpointOverride()) {
      builder.endpointOverride(URI.create(properties.endpoint()));
    }
    return builder;
  }

  static AwsCredentialsProvider credentialsProvider(StorageProperties properties) {
    if (properties.hasStaticCredentials()) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
    return DefaultCredentialsProvider.builder().build();
  }
}
