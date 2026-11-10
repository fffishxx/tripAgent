package com.tripagent.interceptor;

import com.tripagent.common.constant.CommonConstants;
import com.tripagent.common.exception.BusinessException;
import com.tripagent.common.result.ResultCode;
import com.tripagent.context.UserContextHolder;
import com.tripagent.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAuthInterceptorTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserAuthInterceptor interceptor = new UserAuthInterceptor(jwtService);

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        MDC.clear();
    }

    @Test
    void validTokenSetsContextAttributeAndMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CommonConstants.AUTHORIZATION_HEADER, "Bearer user-token");
        when(jwtService.parseUserId("user-token")).thenReturn(7L);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(UserContextHolder.getUserId()).isEqualTo(7L);
        assertThat(request.getAttribute(CommonConstants.REQUEST_USER_ID_ATTR)).isEqualTo(7L);
        assertThat(MDC.get(CommonConstants.MDC_USER_ID)).isEqualTo("7");

        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        assertThat(UserContextHolder.getUserId()).isNull();
        assertThat(MDC.get(CommonConstants.MDC_USER_ID)).isNull();
    }

    @Test
    void missingAuthorizationIsUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertUnauthorized(() -> interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void nonBearerAuthorizationIsUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CommonConstants.AUTHORIZATION_HEADER, "Basic abc");

        assertUnauthorized(() -> interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void optionsRequestPasses() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/trips");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    private void assertUnauthorized(Runnable runnable) {
        BusinessException ex = catchThrowableOfType(runnable::run, BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getResultCode()).isEqualTo(ResultCode.UNAUTHORIZED);
    }
}
