package com.fiapx.videoapi.infrastructure.security;

import com.fiapx.videoapi.domain.exception.InvalidTokenException;
import com.fiapx.videoapi.domain.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String CHANGE_PASSWORD_PATH = "/users/me/password";

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain
  ) throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      String token = header.substring(BEARER_PREFIX.length());
      try {
        UUID userId = jwtService.parseUserId(token);
        Role role = jwtService.parseRole(token);
        if (jwtService.parseMustChangePassword(token) && !isChangePasswordRequest(request)) {
          response.sendError(HttpServletResponse.SC_FORBIDDEN,
              "Troque a senha antes de usar qualquer outro recurso");
          return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            userId, null, authoritiesFor(role)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (InvalidTokenException e) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }

  // Único recurso liberado enquanto o token carrega mustChangePassword=true — sem isso um
  // token válido do admin semeado (ou de qualquer usuário marcado assim) opera o sistema
  // inteiro antes de sair desse estado.
  private boolean isChangePasswordRequest(HttpServletRequest request) {
    return "PUT".equalsIgnoreCase(request.getMethod()) && CHANGE_PASSWORD_PATH.equals(request.getRequestURI());
  }

  private List<GrantedAuthority> authoritiesFor(Role role) {
    return role == Role.ADMIN
        ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
        : List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }
}
