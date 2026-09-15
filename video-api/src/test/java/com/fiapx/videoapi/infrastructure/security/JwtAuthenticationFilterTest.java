package com.fiapx.videoapi.infrastructure.security;

import com.fiapx.videoapi.domain.exception.InvalidTokenException;
import com.fiapx.videoapi.domain.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final FilterChain filterChain = mock(FilterChain.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesAndContinuesWhenMustChangePasswordIsFalse() throws Exception {
    UUID userId = UUID.randomUUID();
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(jwtService.parseUserId("token")).thenReturn(userId);
    when(jwtService.parseRole("token")).thenReturn(Role.USER);
    when(jwtService.parseMustChangePassword("token")).thenReturn(false);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
    verify(filterChain).doFilter(request, response);
    verify(response, never()).sendError(anyInt(), any());
  }

  @Test
  void blocksAnyResourceOtherThanChangePasswordWhenMustChangePasswordIsTrue() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer token");
    when(jwtService.parseUserId("token")).thenReturn(UUID.randomUUID());
    when(jwtService.parseRole("token")).thenReturn(Role.ADMIN);
    when(jwtService.parseMustChangePassword("token")).thenReturn(true);
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
    when(jwtService.parseMustChangePassword("token")).thenReturn(true);
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

  @Test
  void skipsAuthenticationWhenNoAuthorizationHeaderIsPresent() throws Exception {
    when(request.getHeader("Authorization")).thenReturn(null);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  private static int eqForbidden() {
    return HttpServletResponse.SC_FORBIDDEN;
  }
}
