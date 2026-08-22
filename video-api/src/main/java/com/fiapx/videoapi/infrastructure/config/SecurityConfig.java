package com.fiapx.videoapi.infrastructure.config;

import com.fiapx.videoapi.infrastructure.security.CorrelationIdFilter;
import com.fiapx.videoapi.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  private final CorrelationIdFilter correlationIdFilter;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(CorrelationIdFilter correlationIdFilter, JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.correlationIdFilter = correlationIdFilter;
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .anonymous(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/auth/**",
                "/actuator/**",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            ).permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest()
            .authenticated()
        )
        // Uso response.setStatus(...) direto em vez de HttpStatusEntryPoint/AccessDeniedHandlerImpl
        // padrão (que chamam response.sendError(...)): descobri ao vivo que sendError() dispara um
        // forward interno do Tomcat pro /error, reprocessando a cadeia de filtros inteira de novo —
        // como a app é stateless (sem sessão), o SecurityContext não sobrevive a esse forward, e o
        // AuthorizationFilter da segunda passada trata "sem Authentication" como não-autenticado,
        // fazendo até um 403 de verdade (falta de papel ADMIN) virar 401 na resposta final.
        .exceptionHandling(handling -> handling
            .authenticationEntryPoint((request, response, authException) ->
                response.setStatus(HttpStatus.UNAUTHORIZED.value()))
            .accessDeniedHandler((request, response, accessDeniedException) ->
                response.setStatus(HttpStatus.FORBIDDEN.value()))
        )
        // Preciso registrar o JwtAuthenticationFilter primeiro — o comparador de
        // ordem do Spring Security só aceita uma classe de filtro como âncora
        // (JwtAuthenticationFilter.class abaixo) depois que ela própria já foi
        // registrada com uma ordem, o que só acontece neste addFilterBefore.
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(correlationIdFilter, JwtAuthenticationFilter.class);
    return http.build();
  }
}
