package com.tripagent.interceptor;

import com.tripagent.common.constant.CommonConstants;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void validRequestIdIsPropagated() throws Exception {
        String requestId = "trace_ABC-123";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(CommonConstants.REQUEST_ID_HEADER, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcValue = new AtomicReference<>();

        Servlet servlet = mock(Servlet.class);
        filter.doFilter(request, response, new MockFilterChain(
                servlet,
                (Filter) (req, res, chain) -> mdcValue.set(MDC.get(CommonConstants.MDC_REQUEST_ID))));

        assertThat(response.getHeader(CommonConstants.REQUEST_ID_HEADER)).isEqualTo(requestId);
        assertThat(mdcValue.get()).isEqualTo(requestId);
        assertThat(MDC.get(CommonConstants.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void invalidRequestIdIsReplaced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CommonConstants.REQUEST_ID_HEADER, "invalid value!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(CommonConstants.REQUEST_ID_HEADER);
        assertThat(generated).isNotEqualTo("invalid value!");
        assertThat(generated).matches("^[a-f0-9]{32}$");
    }

    @Test
    void missingRequestIdIsGenerated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CommonConstants.REQUEST_ID_HEADER)).isNotBlank();
    }
}
