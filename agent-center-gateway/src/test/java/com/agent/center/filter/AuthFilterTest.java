package com.agent.center.filter;

import com.agent.center.auth.ServiceTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFilterTest {

    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        ServiceTokenManager manager = mock(ServiceTokenManager.class);
        when(manager.getToken()).thenReturn(Mono.just("service-token"));
        filter = new AuthFilter(manager);
        ReflectionTestUtils.setField(filter, "internalToken", "internal-secret");
    }

    @Test
    void missingInternalTokenIsRejected() {
        MockServerWebExchange exchange = exchange(builder -> { });

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, capture(captured)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(captured.get()).isNull();
    }

    @Test
    void userInfoWithoutInternalTokenIsRejected() {
        MockServerWebExchange exchange = exchange(builder -> builder
                .header(AuthFilter.USER_ID_HEADER, "7")
                .header(AuthFilter.USER_TOKEN_HEADER, "user-token"));

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, capture(captured)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(captured.get()).isNull();
    }

    @Test
    void userTokenWithoutUserIdIsRejected() {
        MockServerWebExchange exchange = exchange(builder -> builder
                .header(AuthFilter.INTERNAL_TOKEN_HEADER, "internal-secret")
                .header(AuthFilter.USER_TOKEN_HEADER, "user-token"));

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, capture(captured)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(captured.get()).isNull();
    }

    @Test
    void validInternalCallGetsServiceTokenAndUserHeaders() {
        MockServerWebExchange exchange = exchange(builder -> builder
                .header(AuthFilter.INTERNAL_TOKEN_HEADER, "internal-secret")
                .header(AuthFilter.USER_ID_HEADER, "7")
                .header(AuthFilter.USER_TOKEN_HEADER, "user-token")
                .header(AuthFilter.REQUEST_ID_HEADER, "request-1"));

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, capture(captured)).block();

        ServerWebExchange forwarded = captured.get();
        assertThat(forwarded).isNotNull();
        HttpHeaders headers = forwarded.getRequest().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer service-token");
        assertThat(headers.getFirst(AuthFilter.USER_ID_HEADER)).isEqualTo("7");
        assertThat(headers.getFirst(AuthFilter.USER_TOKEN_HEADER)).isEqualTo("user-token");
        assertThat(headers.getFirst(AuthFilter.REQUEST_FROM_HEADER))
                .isEqualTo("agent-center-gateway");
        assertThat(headers.getFirst(AuthFilter.INTERNAL_TOKEN_HEADER)).isNull();
        assertThat(headers.getFirst(AuthFilter.REQUEST_ID_HEADER)).isEqualTo("request-1");
    }

    @Test
    void anonymousInternalCallDoesNotLeakUserHeaders() {
        MockServerWebExchange exchange = exchange(builder -> builder
                .header(AuthFilter.INTERNAL_TOKEN_HEADER, "internal-secret")
                .header(AuthFilter.USER_ID_HEADER, "999"));

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, capture(captured)).block();

        // 有 X-User-Id 没有 X-User-Token，直接拒绝
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authTokenPathWithoutInternalTokenIsRejected() {
        MockServerWebExchange exchange = exchange("/auth/token", builder -> { });

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, capture(captured)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(captured.get()).isNull();
    }

    @Test
    void publicHealthPathBypassesAuth() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/health").build());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        filter.filter(exchange, capture(captured)).block();

        assertThat(captured.get()).isNotNull();
    }

    private MockServerWebExchange exchange(
            java.util.function.Consumer<MockServerHttpRequest.BaseBuilder<?>> customizer) {
        return exchange("/acs/chat", customizer);
    }

    private MockServerWebExchange exchange(
            String path,
            java.util.function.Consumer<MockServerHttpRequest.BaseBuilder<?>> customizer) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.method(HttpMethod.POST, path);
        customizer.accept(builder);
        return MockServerWebExchange.from(builder.build());
    }

    private GatewayFilterChain capture(AtomicReference<ServerWebExchange> captured) {
        return exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
    }
}
