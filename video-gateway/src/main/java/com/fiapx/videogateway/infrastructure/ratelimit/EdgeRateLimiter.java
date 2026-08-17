package com.fiapx.videogateway.infrastructure.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fiapx.videogateway.infrastructure.config.GatewayRouteProperties;

@Component
public class EdgeRateLimiter {

	private static final String KEY_PREFIX = "gateway_rate_limit:";

	private final StringRedisTemplate redisTemplate;
	private final GatewayRouteProperties properties;

	public EdgeRateLimiter(StringRedisTemplate redisTemplate, GatewayRouteProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	public boolean tryConsume(String clientKey) {
		String key = KEY_PREFIX + clientKey;
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redisTemplate.expire(key, Duration.ofSeconds(properties.rateLimit().periodSeconds()));
		}
		return count != null && count <= properties.rateLimit().capacity();
	}
}
