package com.tripagent.interceptor;

import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.config.AgentServiceProperties;
import com.tripagent.context.UserContextHolder;
import com.tripagent.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceInterceptorTest {

    private AgentServiceInterceptor interceptor;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AgentServiceProperties properties = new AgentServiceProperties();
        properties.setServiceToken("service-secret");
        jwtService = mock(JwtService.class);
        when(jwtService.parseUserId("user-token")).thenReturn(7L);
        interceptor = new AgentServiceInterceptor(properties, jwtService);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        MDC.clear();
    }

    @Test
    void missingServiceTokenIsForbidden() {
        assertCode(new MockHttpServletRequest(), ResultCode.SERVICE_AUTH_FAILED);
    }

    @Test
    void wrongServiceTokenIsForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CommonConstants.SERVICE_TOKEN_HEADER, "wrong");

        assertCode(request, ResultCode.SERVICE_AUTH_FAILED);
    }

    @Test
    void missingUserTokenIsUnauthorized() {
        MockHttpServletRequest request = requestWithServiceToken();

        assertCode(request, ResultCode.UNAUTHORIZED);
    }

    @Test
    void mismatchedUserIdIsForbidden() {
        MockHttpServletRequest request = requestWithServiceToken();
        request.addHeader(CommonConstants.USER_TOKEN_HEADER, "user-token");
        request.addHeader(CommonConstants.USER_ID_HEADER, "8");

        assertCode(request, ResultCode.FORBIDDEN);
    }

    @Test
    void validServiceAndUserIdentityAreRecorded() {
        MockHttpServletRequest request = requestWithServiceToken();
        request.addHeader(CommonConstants.USER_TOKEN_HEADER, "user-token");
        request.addHeader(CommonConstants.USER_ID_HEADER, "7");
        request.addHeader(CommonConstants.AGENT_ID_HEADER, "1004");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(UserContextHolder.getUserId()).isEqualTo(7L);
        assertThat(request.getAttribute(CommonConstants.REQUEST_USER_ID_ATTR)).isEqualTo(7L);
        assertThat(request.getAttribute(CommonConstants.REQUEST_USER_TOKEN_ATTR)).isEqualTo("user-token");
        assertThat(MDC.get(CommonConstants.MDC_USER_ID)).isEqualTo("7");
        assertThat(MDC.get(CommonConstants.MDC_AGENT_ID)).isEqualTo("1004");

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(UserContextHolder.getUserId()).isNull();
        assertThat(MDC.get(CommonConstants.MDC_USER_ID)).isNull();
        assertThat(MDC.get(CommonConstants.MDC_AGENT_ID)).isNull();
    }

    @Test
    void invalidAgentIdFallsBackToDash() {
        MockHttpServletRequest request = requestWithServiceToken();
        request.addHeader(CommonConstants.USER_TOKEN_HEADER, "user-token");
        request.addHeader(CommonConstants.USER_ID_HEADER, "7");
        request.addHeader(CommonConstants.AGENT_ID_HEADER, "invalid value!");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(request.getAttribute(CommonConstants.REQUEST_AGENT_ID_ATTR))
                .isEqualTo(CommonConstants.DEFAULT_LOG_VALUE);
    }

    @Test
    void optionsRequestPasses() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/agent/v1/trips");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    private MockHttpServletRequest requestWithServiceToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CommonConstants.SERVICE_TOKEN_HEADER, "service-secret");
        return request;
    }

    private void assertCode(MockHttpServletRequest request, ResultCode expected) {
        BusinessException ex = catchThrowableOfType(
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getResultCode()).isEqualTo(expected);
    }
}
