package com.fiapx.videogateway.infrastructure.ratelimit;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class RateLimitFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

	private final EdgeRateLimiter rateLimiter;

	public RateLimitFilterFunction(EdgeRateLimiter rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	@Override
	public @NonNull ServerResponse filter(ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
		String clientKey = request.remoteAddress().map(addr -> addr.getAddress().getHostAddress()).orElse("unknown");
		if (!rateLimiter.tryConsume(clientKey)) {
			return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		return next.handle(request);
	}
}
