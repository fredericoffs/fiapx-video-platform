package com.fiapx.videoapi.infrastructure.security;

import com.fiapx.videoapi.domain.port.LoginRateLimiter;
import com.fiapx.videoapi.infrastructure.config.LoginRateLimitProperties;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisLoginRateLimiter implements LoginRateLimiter {

  private static final String KEY_PREFIX = "login_attempts:";

  private final StringRedisTemplate redisTemplate;
  private final LoginRateLimitProperties properties;

  public RedisLoginRateLimiter(StringRedisTemplate redisTemplate, LoginRateLimitProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  @Override
  public boolean isBlocked(String email) {
    String value = redisTemplate.opsForValue().get(key(email));
    return value != null && Integer.parseInt(value) >= properties.maxAttempts();
  }

  @Override
  public void registerFailedAttempt(String email) {
    String key = key(email);
    Long attempts = redisTemplate.opsForValue().increment(key);
    if (attempts != null && attempts == 1L) {
      redisTemplate.expire(key, Duration.ofSeconds(properties.windowSeconds()));
    }
  }

  @Override
  public void reset(String email) {
    redisTemplate.delete(key(email));
  }

  private String key(String email) {
    return KEY_PREFIX + email;
  }
}
