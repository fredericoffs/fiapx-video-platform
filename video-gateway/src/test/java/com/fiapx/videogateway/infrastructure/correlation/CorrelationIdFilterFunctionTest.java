package com.fiapx.videogateway.infrastructure.correlation;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
