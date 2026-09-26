package com.fiapx.videoapi;

import com.fiapx.videoapi.infrastructure.security.RedisLoginRateLimiter;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisLoginRateLimiterTest extends AbstractSqsIntegrationTest {

  @Autowired
  private RedisLoginRateLimiter rateLimiter;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @AfterEach
  void cleanup() {
    Set<String> keys = redisTemplate.keys("login_attempts:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void isNotBlockedBeforeAnyFailedAttempt() {
    String email = newEmail();

    assertThat(rateLimiter.isBlocked(email)).isFalse();
  }

  @Test
  void staysUnblockedBelowMaxAttempts() {
    String email = newEmail();

    for (int i = 0; i < 4; i++) {
      rateLimiter.registerFailedAttempt(email);
    }

    assertThat(rateLimiter.isBlocked(email)).isFalse();
  }

  @Test
  void becomesBlockedAtMaxAttempts() {
    String email = newEmail();

    for (int i = 0; i < 5; i++) {
      rateLimiter.registerFailedAttempt(email);
    }

    assertThat(rateLimiter.isBlocked(email)).isTrue();
  }

  @Test
  void resetClearsBlockedState() {
    String email = newEmail();
    for (int i = 0; i < 5; i++) {
      rateLimiter.registerFailedAttempt(email);
    }
    assertThat(rateLimiter.isBlocked(email)).isTrue();

    rateLimiter.reset(email);

    assertThat(rateLimiter.isBlocked(email)).isFalse();
  }

  @Test
  void doesNotAffectAttemptCounterOfOtherEmails() {
    String blockedEmail = newEmail();
    String otherEmail = newEmail();

    for (int i = 0; i < 5; i++) {
      rateLimiter.registerFailedAttempt(blockedEmail);
    }

    assertThat(rateLimiter.isBlocked(blockedEmail)).isTrue();
    assertThat(rateLimiter.isBlocked(otherEmail)).isFalse();
  }

  @Test
  void setsExpirationOnFirstFailedAttempt() {
    String email = newEmail();

    rateLimiter.registerFailedAttempt(email);

    Long ttl = redisTemplate.getExpire("login_attempts:" + email);
    assertThat(ttl).isNotNull();
    assertThat(ttl).isPositive();
    assertThat(ttl).isLessThanOrEqualTo(60L);
  }

  private String newEmail() {
    return "rate-limit-" + UUID.randomUUID() + "@fiapx.com";
  }
}
