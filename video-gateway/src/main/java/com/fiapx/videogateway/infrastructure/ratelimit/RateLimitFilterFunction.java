package com.fiapx.videogateway.infrastructure.ratelimit;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.fiapx.videogateway.infrastructure.config.GatewayRouteProperties;

@Component
public class RateLimitFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

	private final EdgeRateLimiter rateLimiter;
	private final GatewayRouteProperties properties;

	public RateLimitFilterFunction(EdgeRateLimiter rateLimiter, GatewayRouteProperties properties) {
		this.rateLimiter = rateLimiter;
		this.properties = properties;
	}

	@Override
	public @NonNull ServerResponse filter(ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
		String clientKey = request.remoteAddress().map(addr -> addr.getAddress().getHostAddress()).orElse("unknown");
		if (!rateLimiter.tryConsume(clientKey)) {
			long periodSeconds = properties.rateLimit().periodSeconds();
			ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
					HttpStatus.TOO_MANY_REQUESTS,
					"Limite de " + properties.rateLimit().capacity() + " requisições a cada " + periodSeconds
							+ "s excedido para este cliente. Tente novamente mais tarde."
			);
			problemDetail.setTitle("Muitas requisições");
			return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
					.header(HttpHeaders.RETRY_AFTER, String.valueOf(periodSeconds))
					.contentType(MediaType.APPLICATION_PROBLEM_JSON)
					.body(problemDetail);
		}
		return next.handle(request);
	}
}
