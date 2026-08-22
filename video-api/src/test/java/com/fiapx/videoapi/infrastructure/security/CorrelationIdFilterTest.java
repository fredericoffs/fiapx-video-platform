package com.fiapx.videoapi.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void reusesTheIncomingCorrelationIdHeaderInMdcDuringTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] seenDuringChain = new String[1];
    MockFilterChain chain = new MockFilterChain() {
      @Override
      public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res) {
        seenDuringChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
      }
    };

    filter.doFilter(request, response, chain);

    assertThat(seenDuringChain[0]).isEqualTo("abc-123");
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void generatesACorrelationIdWhenTheHeaderIsAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] seenDuringChain = new String[1];
    MockFilterChain chain = new MockFilterChain() {
      @Override
      public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res) {
        seenDuringChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
      }
    };

    filter.doFilter(request, response, chain);

    assertThat(UUID.fromString(seenDuringChain[0])).isNotNull();
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }
}
