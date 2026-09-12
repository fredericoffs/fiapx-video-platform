package com.fiapx.videoapi.infrastructure.web;

import com.fiapx.videoapi.domain.exception.EmailAlreadyRegisteredException;
import com.fiapx.videoapi.domain.exception.InvalidCredentialsException;
import com.fiapx.videoapi.domain.exception.InvalidFilenameException;
import com.fiapx.videoapi.domain.exception.LoginRateLimitExceededException;
import com.fiapx.videoapi.domain.exception.UnsupportedVideoFormatException;
import com.fiapx.videoapi.domain.exception.UserNotFoundException;
import com.fiapx.videoapi.domain.exception.VideoNotCompletedException;
import com.fiapx.videoapi.domain.exception.VideoNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Decidi que todas as respostas aqui sejam {@code application/problem+json} (RFC 7807, via
 * {@link ProblemDetail}) — nunca HTML. Erros não tratados aqui (ex.: falha de validação em
 * {@code @Valid}) seguem o mesmo formato porque liguei {@code spring.mvc.problemdetails.enabled=true}
 * em application.yml. O navegador não interpreta JSON como script; combino isso ao
 * header {@code X-Content-Type-Options: nosniff} (padrão do Spring Security, que não desativei em
 * SecurityConfig) pra fechar a classe de XSS refletido que scanners de SAST costumam apontar de
 * forma genérica em qualquer sink de resposta HTTP que embuta input do usuário (e-mail, nome
 * de arquivo), independentemente do Content-Type real.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(VideoNotFoundException.class)
  public ProblemDetail handleVideoNotFound(VideoNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "Vídeo não encontrado", e.getMessage());
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFound(UserNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "Usuário não encontrado", e.getMessage());
  }

  @ExceptionHandler(VideoNotCompletedException.class)
  public ProblemDetail handleVideoNotCompleted(VideoNotCompletedException e) {
    return problem(HttpStatus.CONFLICT, "Vídeo ainda não processado", e.getMessage());
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
    return problem(HttpStatus.CONFLICT, "E-mail já cadastrado", e.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
    return problem(HttpStatus.UNAUTHORIZED, "Credenciais inválidas", e.getMessage());
  }

  @ExceptionHandler(LoginRateLimitExceededException.class)
  public ProblemDetail handleLoginRateLimitExceeded(LoginRateLimitExceededException e) {
    return problem(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas de login", e.getMessage());
  }

  @ExceptionHandler(UnsupportedVideoFormatException.class)
  public ProblemDetail handleUnsupportedVideoFormat(UnsupportedVideoFormatException e) {
    return problem(HttpStatus.BAD_REQUEST, "Formato de vídeo não suportado", e.getMessage());
  }

  @ExceptionHandler(InvalidFilenameException.class)
  public ProblemDetail handleInvalidFilename(InvalidFilenameException e) {
    return problem(HttpStatus.BAD_REQUEST, "Nome de arquivo inválido", e.getMessage());
  }

  private ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    return problemDetail;
  }
}
