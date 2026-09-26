package com.fiapx.videoapi.infrastructure.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void mapsMaxUploadSizeExceededTo413() {
    ProblemDetail problem = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(500L * 1024 * 1024));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    assertThat(problem.getTitle()).isEqualTo("Arquivo excede o tamanho máximo permitido");
  }

  // Item 12 da revisão crítica: @Min/@Max em @RequestParam lançam ConstraintViolationException
  // via MethodValidationInterceptor — sem handler próprio, page/size fora do intervalo vazava
  // como 500 em vez de 400 (só apareceu rodando o teste de integração real, com Docker).
  @Test
  void mapsConstraintViolationTo400WithEachViolationInTheDetail() {
    Path path = mock(Path.class);
    when(path.toString()).thenReturn("list.page");
    ConstraintViolation<?> violation = mock(ConstraintViolation.class);
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must be greater than or equal to 0");

    ProblemDetail problem = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getTitle()).isEqualTo("Parâmetro inválido");
    assertThat(problem.getDetail()).isEqualTo("list.page: must be greater than or equal to 0");
  }
}
