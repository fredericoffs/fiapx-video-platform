package com.fiapx.videogateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fecha a pendência registrada na Sprint 2: video-gateway só tinha teste de contexto,
 * sem cobertura automatizada de roteamento/rate-limit. O backend real (video-api) é
 * substituído por um HttpServer JDK apontado via fiapx.gateway.video-api-uri.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GatewayRoutingIntegrationTest {

  private static HttpServer stubVideoApi;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @DynamicPropertySource
  static void gatewayProperties(DynamicPropertyRegistry registry) throws IOException {
    stubVideoApi = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    stubVideoApi.createContext("/auth/login", exchange -> respond(exchange, "{\"stub\":\"auth\"}"));
    stubVideoApi.createContext("/videos", exchange -> respond(exchange, "{\"stub\":\"videos\"}"));
    stubVideoApi.createContext("/admin/users", exchange -> respond(exchange, "{\"stub\":\"admin\"}"));
    stubVideoApi.setExecutor(null);
    stubVideoApi.start();

    registry.add("fiapx.gateway.video-api-uri",
        () -> "http://localhost:" + stubVideoApi.getAddress().getPort());
    registry.add("fiapx.gateway.rate-limit.capacity", () -> "3");
  }

  @AfterAll
  static void stopStubVideoApi() {
    if (stubVideoApi != null) {
      stubVideoApi.stop(0);
    }
  }

  @AfterEach
  void clearRateLimitKeys() {
    Set<String> keys = redisTemplate.keys("gateway_rate_limit:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void routesAuthRequestsToVideoApi() throws Exception {
    mockMvc.perform(post("/auth/login").content("{}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"stub\":\"auth\"}"));
  }

  @Test
  void routesVideosRequestsToVideoApi() throws Exception {
    mockMvc.perform(get("/videos"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"stub\":\"videos\"}"));
  }

  @Test
  void routesAdminRequestsToVideoApi() throws Exception {
    mockMvc.perform(get("/admin/users"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"stub\":\"admin\"}"));
  }

  @Test
  void unmappedPathIsNotProxiedToVideoApi() throws Exception {
    mockMvc.perform(get("/unmapped/path")).andExpect(status().isNotFound());
  }

  @Test
  void edgeRateLimitBlocksAfterCapacityExceeded() throws Exception {
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(get("/videos")).andExpect(status().isOk());
    }

    mockMvc.perform(get("/videos")).andExpect(status().isTooManyRequests());
  }

  private static void respond(HttpExchange exchange, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}
