package com.vayvora.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Route filter referenced as {@code AuthorizationFilter} in application.yml.
 *
 * <p>Verifies the HS256 access token issued by auth-service and forwards the
 * caller's identity to downstream services as {@code X-User-Id},
 * {@code X-Organization-Id} and {@code X-User-Role}. Any client-supplied
 * copies of those headers are dropped first, since services trust them.
 *
 * <p>Browsers cannot set headers on a WebSocket handshake, so the token may
 * also be passed as an {@code access_token} query parameter; it is removed
 * before the request is forwarded.
 */
@Slf4j
@Component
public class AuthorizationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AuthorizationGatewayFilterFactory.Config> {

    static final String USER_ID = "X-User-Id";
    static final String ORGANIZATION_ID = "X-Organization-Id";
    static final String USER_ROLE = "X-User-Role";
    static final String TOKEN_PARAM = "access_token";

    private final SecretKey key;
    private final boolean enabled;

    public AuthorizationGatewayFilterFactory(
            @Value("${vayvora.auth.jwt-secret}") String secret,
            @Value("${vayvora.auth.enabled:true}") boolean enabled) {
        super(Config.class);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.enabled = enabled;
        if (!enabled) {
            log.warn("Gateway authorization is DISABLED — requests are forwarded without a token check");
        }
    }

    @Override
    public String name() {
        return "AuthorizationFilter";
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            if (!enabled || request.getMethod() == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }

            String token = extractToken(request);
            if (token == null) {
                return reject(exchange, "Missing bearer token");
            }

            Claims claims;
            try {
                claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            } catch (JwtException | IllegalArgumentException e) {
                return reject(exchange, "Invalid or expired token");
            }

            ServerHttpRequest forwarded = request.mutate()
                    .uri(withoutTokenParam(request.getURI()))
                    .headers(h -> {
                        h.remove(USER_ID);
                        h.remove(ORGANIZATION_ID);
                        h.remove(USER_ROLE);
                        setIfPresent(h, USER_ID, claims.getSubject());
                        setIfPresent(h, ORGANIZATION_ID, claims.get("organizationId"));
                        setIfPresent(h, USER_ROLE, claims.get("role"));
                    })
                    .build();
            return chain.filter(exchange.mutate().request(forwarded).build());
        };
    }

    static String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        List<String> param = request.getQueryParams().get(TOKEN_PARAM);
        return param == null || param.isEmpty() || param.get(0).isBlank() ? null : param.get(0);
    }

    static URI withoutTokenParam(URI uri) {
        if (uri.getRawQuery() == null || !uri.getRawQuery().contains(TOKEN_PARAM)) return uri;
        return UriComponentsBuilder.fromUri(uri).replaceQueryParam(TOKEN_PARAM).build(true).toUri();
    }

    private static void setIfPresent(HttpHeaders headers, String name, Object value) {
        if (value != null && !value.toString().isBlank()) headers.set(name, value.toString());
    }

    private static Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /** No per-route options yet; present for the filter factory contract. */
    public static class Config {
    }
}
