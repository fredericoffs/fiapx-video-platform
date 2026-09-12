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

	static final String FORWARDED_FOR = "X-Forwarded-For";

	@Override
	public @NonNull ServerResponse filter(ServerRequest request, @NonNull HandlerFunction<ServerResponse> next) throws Exception {
		String clientKey = resolveClientKey(request);
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

	/**
	 * Atrás do ingress-nginx/ELB o remoteAddress é sempre o IP do proxy — sem olhar o
	 * X-Forwarded-For todos os usuários dividiriam a mesma cota. Uso o primeiro IP da
	 * cadeia (o cliente original); sem o header, caio no endereço direto da conexão.
	 */
	private static String resolveClientKey(ServerRequest request) {
		String forwardedFor = request.headers().firstHeader(FORWARDED_FOR);
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			String first = forwardedFor.split(",")[0].strip();
			if (!first.isEmpty()) {
				return first;
			}
		}
		return request.remoteAddress().map(addr -> addr.getAddress().getHostAddress()).orElse("unknown");
	}
}
