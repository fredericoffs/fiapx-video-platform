package com.fiapx.videoapi.application.usecase;

import java.time.Duration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fiapx.videoapi.application.dto.LoginCommand;
import com.fiapx.videoapi.application.dto.LoginResult;
import com.fiapx.videoapi.domain.exception.InvalidCredentialsException;
import com.fiapx.videoapi.domain.exception.LoginRateLimitExceededException;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.LoginRateLimiter;
import com.fiapx.videoapi.domain.port.TokenIssuer;
import com.fiapx.videoapi.domain.port.UserRepository;
import com.fiapx.videoapi.infrastructure.config.JwtProperties;

@Service
public class LoginUseCase {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenIssuer tokenIssuer;
	private final LoginRateLimiter loginRateLimiter;
	private final JwtProperties jwtProperties;

	public LoginUseCase(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			TokenIssuer tokenIssuer,
			LoginRateLimiter loginRateLimiter,
			JwtProperties jwtProperties
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuer = tokenIssuer;
		this.loginRateLimiter = loginRateLimiter;
		this.jwtProperties = jwtProperties;
	}

	public LoginResult handle(LoginCommand command) {
		String email = command.email();
		if (loginRateLimiter.isBlocked(email)) {
			throw new LoginRateLimitExceededException(email);
		}

		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null || !passwordEncoder.matches(command.rawPassword(), user.getPasswordHash())) {
			loginRateLimiter.registerFailedAttempt(email);
			throw new InvalidCredentialsException();
		}

		loginRateLimiter.reset(email);
		String token = tokenIssuer.generateToken(user.getId(), user.getRole());
		long expiresInSeconds = Duration.ofMinutes(jwtProperties.expirationMinutes()).toSeconds();
		return new LoginResult(token, expiresInSeconds, user.getRole());
	}
}
