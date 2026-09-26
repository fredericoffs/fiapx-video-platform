package com.fiapx.videoapi.infrastructure.messaging.sqs;

import com.fiapx.videoapi.infrastructure.config.SqsProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration
public class SqsClientConfig {

  @Bean
  public SqsClient sqsClient(SqsProperties properties) {
    return configure(SqsClient.builder(), properties).build();
  }

  static SqsClientBuilder configure(SqsClientBuilder builder, SqsProperties properties) {
    builder.region(Region.of(properties.region())).credentialsProvider(credentialsProvider(properties));
    if (properties.hasEndpointOverride()) {
      builder.endpointOverride(URI.create(properties.endpoint()));
    }
    return builder;
  }

  static AwsCredentialsProvider credentialsProvider(SqsProperties properties) {
    if (properties.hasStaticCredentials()) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
    return DefaultCredentialsProvider.builder().build();
  }
}
