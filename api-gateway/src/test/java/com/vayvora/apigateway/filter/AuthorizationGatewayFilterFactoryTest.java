package com.vayvora.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationGatewayFilterFactoryTest {
    private static final String SECRET = "test_secret_that_is_at_least_32_bytes_long!";

    private final GatewayFilter filter =
            new AuthorizationGatewayFilterFactory(SECRET, true).apply(new AuthorizationGatewayFilterFactory.Config());

    private final AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange.getRequest());
        return Mono.empty();
    };

    private static String token(String secret, Date expiry) {
        return Jwts.builder()
                .subject("42")
                .claim("organizationId", 7)
                .claim("role", "ADMIN")
                .expiration(expiry)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String validToken() {
        return token(SECRET, new Date(System.currentTimeMillis() + 60_000));
    }

    @Test
    void rejectsMissingToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/calls/1"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String forged = token("another_secret_that_is_also_32_bytes_long", new Date(System.currentTimeMillis() + 60_000));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/calls/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsExpiredToken() {
        String expired = token(SECRET, new Date(System.currentTimeMillis() - 60_000));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/calls/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forwardsIdentityFromClaimsAndDropsSpoofedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/calls/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .header("X-Organization-Id", "999"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.get("X-Organization-Id")).containsExactly("7");
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void acceptsWebSocketQueryTokenAndStripsIt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/media-stream?session_id=call_01&access_token=" + validToken() + "&domain=edusaas"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getURI().getQuery()).isEqualTo("session_id=call_01&domain=edusaas");
        assertThat(forwarded.get().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
    }

    @Test
    void passesThroughWhenDisabled() {
        GatewayFilter open = new AuthorizationGatewayFilterFactory(SECRET, false)
                .apply(new AuthorizationGatewayFilterFactory.Config());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/calls/1"));

        open.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
    }
}
