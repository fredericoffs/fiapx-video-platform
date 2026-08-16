package com.fiapx.videoapi.infrastructure.storage;

import com.fiapx.videoapi.infrastructure.config.StorageProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3StorageConfig {

  @Bean
  public S3Client s3Client(StorageProperties storageProperties) {
    return S3Client.builder()
        .endpointOverride(URI.create(storageProperties.endpoint()))
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storageProperties.accessKey(), storageProperties.secretKey())
            )
        )
        .forcePathStyle(true)
        .build();
  }
}
