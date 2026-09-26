package com.fiapx.notificationworker;

import com.fiapx.notificationworker.domain.model.NotificationChannelType;
import com.fiapx.notificationworker.domain.port.NotificationChannel;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebhookChannelIntegrationTest extends AbstractSqsIntegrationTest {

  private static MockWebServer mockWebServer;

  @DynamicPropertySource
  static void webhookUrl(DynamicPropertyRegistry registry) throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    registry.add("fiapx.notification.webhook-fallback-url", () -> mockWebServer.url("/webhook").toString());
  }

  @AfterAll
  static void stopServer() throws IOException {
    mockWebServer.shutdown();
  }

  @Autowired
  private List<NotificationChannel> channels;

  @Test
  void postsVideoIdErrorMessageAndRecipientEmailToConfiguredUrl() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    UUID videoId = UUID.randomUUID();

    webhookChannel().send(videoId, "ffmpeg falhou", "dono@example.com").join();

    RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath()).isEqualTo("/webhook");
    String body = request.getBody().readUtf8();
    assertThat(body).contains(videoId.toString()).contains("ffmpeg falhou").contains("dono@example.com");
  }

  @Test
  void toleratesNullRecipientEmailWithoutThrowing() throws InterruptedException {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    UUID videoId = UUID.randomUUID();

    webhookChannel().send(videoId, "ffmpeg falhou", null).join();

    RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
  }

  private NotificationChannel webhookChannel() {
    return channels.stream()
        .filter(channel -> channel.type() == NotificationChannelType.WEBHOOK)
        .findFirst()
        .orElseThrow();
  }
}
