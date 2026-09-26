package com.fiapx.videoapi.infrastructure.security;

import com.fiapx.videoapi.domain.exception.InvalidTokenException;
import com.fiapx.videoapi.domain.model.Role;
import com.fiapx.videoapi.domain.model.User;
import com.fiapx.videoapi.domain.port.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

  private final JwtService jwtService = mock(JwtService.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final FilterChain filterChain = mock(FilterChain.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  /** Usuário com um token emitido bem depois de qualquer revogação — passa em hasValidToken. */
  private static User existingUser(UUID userId, Role role) {
    return new User(userId, "user@example.com", "hash", role, Instant.now().minusSeconds(3600));
  }

  @Test
  void authenticatesAndContinuesWhenMustChangePasswordIsFalse() throws Exception {
    UUID userId = UUID.randomUUID();
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(jwtService.parseUserId("token")).thenReturn(userId);
    when(jwtService.parseRole("token")).thenReturn(Role.USER);
    when(jwtService.parseIssuedAt("token")).thenReturn(Instant.now());
    when(jwtService.parseMustChangePassword("token")).thenReturn(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser(userId, Role.USER)));

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
    verify(filterChain).doFilter(request, response);
    verify(response, never()).sendError(anyInt(), any());
  }

  @Test
  void blocksAnyResourceOtherThanChangePasswordWhenMustChangePasswordIsTrue() throws Exception {
    UUID userId = UUID.randomUUID();
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(jwtService.parseUserId("token")).thenReturn(userId);
    when(jwtService.parseRole("token")).thenReturn(Role.ADMIN);
    when(jwtService.parseIssuedAt("token")).thenReturn(Instant.now());
    when(jwtService.parseMustChangePassword("token")).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser(userId, Role.ADMIN)));
    when(request.getRequestURI()).thenReturn("/admin/users");
    when(request.getMethod()).thenReturn("GET");

    filter.doFilterInternal(request, response, filterChain);

    verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), any());
    verify(filterChain, never()).doFilter(any(), any());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void allowsTheChangePasswordEndpointWhenMustChangePasswordIsTrue() throws Exception {
    UUID userId = UUID.randomUUID();
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(jwtService.parseUserId("token")).thenReturn(userId);
    when(jwtService.parseRole("token")).thenReturn(Role.ADMIN);
    when(jwtService.parseIssuedAt("token")).thenReturn(Instant.now());
    when(jwtService.parseMustChangePassword("token")).thenReturn(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser(userId, Role.ADMIN)));
    when(request.getRequestURI()).thenReturn("/users/me/password");
    when(request.getMethod()).thenReturn("PUT");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
    verify(filterChain).doFilter(request, response);
    verify(response, never()).sendError(anyInt(), any());
  }

  @Test
  void clearsContextWhenTokenIsInvalid() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(jwtService.parseUserId("token")).thenThrow(new InvalidTokenException("inválido", null));

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  // Item 14: usuário excluído — o próprio lookup falha, o token vira inválido mesmo sem
  // estar expirado nem ter sido "revogado" por uma troca de senha específica.
  @Test
  void treatsTokenAsInvalidWhenUserNoLongerExists() throws Exception {
    UUID userId = UUID.randomUUID();
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(jwtService.parseUserId("token")).thenReturn(userId);
    when(jwtService.parseRole("token")).thenReturn(Role.USER);
    when(jwtService.parseIssuedAt("token")).thenReturn(Instant.now());
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  // Item 14: token emitido antes da última troca de senha do usuário — mesmo assinado
  // corretamente e sem estar expirado, não pode mais autenticar.
  @Test
  void treatsTokenAsInvalidWhenIssuedBeforePasswordWasChanged() throws Exception {
    UUID userId = UUID.randomUUID();
    User user = existingUser(userId, Role.USER).withPasswordChanged("new-hash");
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(jwtService.parseUserId("token")).thenReturn(userId);
    when(jwtService.parseRole("token")).thenReturn(Role.USER);
    when(jwtService.parseIssuedAt("token")).thenReturn(Instant.now().minusSeconds(60));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void skipsAuthenticationWhenNoAuthorizationHeaderIsPresent() throws Exception {
    when(request.getHeader("Authorization")).thenReturn(null);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }
}
