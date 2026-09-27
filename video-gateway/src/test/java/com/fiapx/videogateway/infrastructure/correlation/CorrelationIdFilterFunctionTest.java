package com.fiapx.videogateway.infrastructure.correlation;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdFilterFunctionTest {

  private final CorrelationIdFilterFunction filterFunction = new CorrelationIdFilterFunction();

  @SuppressWarnings("unchecked")
  @Test
  void reusesTheIncomingCorrelationIdHeader() throws Exception {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/videos");
    servletRequest.addHeader(CorrelationIdFilterFunction.HEADER_NAME, "abc-123");
    ServerRequest request = ServerRequest.create(servletRequest, List.of());
    HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
    ServerResponse expectedResponse = ServerResponse.ok().build();
    when(next.handle(any(ServerRequest.class))).thenReturn(expectedResponse);

    ServerResponse response = filterFunction.filter(request, next);

    ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);
    verify(next).handle(captor.capture());
    assertThat(captor.getValue().headers().firstHeader(CorrelationIdFilterFunction.HEADER_NAME)).isEqualTo("abc-123");
    assertThat(response).isSameAs(expectedResponse);
  }

  @SuppressWarnings("unchecked")
  @Test
  void generatesACorrelationIdWhenTheHeaderIsAbsent() throws Exception {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/videos");
    ServerRequest request = ServerRequest.create(servletRequest, List.of());
    HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
    when(next.handle(any(ServerRequest.class))).thenReturn(ServerResponse.ok().build());

    filterFunction.filter(request, next);

    ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);
    verify(next).handle(captor.capture());
    String generated = captor.getValue().headers().firstHeader(CorrelationIdFilterFunction.HEADER_NAME);
    assertThat(UUID.fromString(generated)).isNotNull();
  }

  @SuppressWarnings("unchecked")
  @Test
  void generatesACorrelationIdWhenTheHeaderIsBlank() throws Exception {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/videos");
    servletRequest.addHeader(CorrelationIdFilterFunction.HEADER_NAME, "   ");
    ServerRequest request = ServerRequest.create(servletRequest, List.of());
    HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
    when(next.handle(any(ServerRequest.class))).thenReturn(ServerResponse.ok().build());

    filterFunction.filter(request, next);

    ArgumentCaptor<ServerRequest> captor = ArgumentCaptor.forClass(ServerRequest.class);
    verify(next).handle(captor.capture());
    String generated = captor.getValue().headers().firstHeader(CorrelationIdFilterFunction.HEADER_NAME);
    assertThat(UUID.fromString(generated)).isNotNull();
  }

  @Test
  void keepsTheIdInTheMdcDuringTheCallAndReturnsItOnTheResponse() throws Exception {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/videos");
    servletRequest.addHeader(CorrelationIdFilterFunction.HEADER_NAME, "abc-123");
    ServerRequest request = ServerRequest.create(servletRequest, List.of());
    String[] seenInMdc = new String[1];
    HandlerFunction<ServerResponse> next = req -> {
      seenInMdc[0] = MDC.get(CorrelationIdFilterFunction.MDC_KEY);
      return ServerResponse.status(HttpStatus.CREATED).build();
    };

    MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest, servletResponse));
    try {
      filterFunction.filter(request, next);
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }

    assertThat(seenInMdc[0]).isEqualTo("abc-123");
    assertThat(servletResponse.getHeader(CorrelationIdFilterFunction.HEADER_NAME)).isEqualTo("abc-123");
    assertThat(MDC.get(CorrelationIdFilterFunction.MDC_KEY)).isNull();
  }

  @Test
  void logsWritesAndErrorsButNotSuccessfulReads(CapturedOutput output) throws Exception {
    filterFunction.filter(ServerRequest.create(new MockHttpServletRequest("POST", "/videos"), List.of()),
        req -> ServerResponse.status(HttpStatus.CREATED).build());
    filterFunction.filter(ServerRequest.create(new MockHttpServletRequest("GET", "/videos/1"), List.of()),
        req -> ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS).build());
    filterFunction.filter(ServerRequest.create(new MockHttpServletRequest("GET", "/videos"), List.of()),
        req -> ServerResponse.ok().build());

    assertThat(output).contains("POST /videos -> 201").contains("GET /videos/1 -> 429")
        .doesNotContain("GET /videos -> 200");
  }
}
