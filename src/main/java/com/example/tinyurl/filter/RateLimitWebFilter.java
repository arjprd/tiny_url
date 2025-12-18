package com.example.tinyurl.filter;

import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RateLimitWebFilter implements WebFilter, Ordered {

    // Run after security filter (which is typically -100) but before other filters
    private static final int FILTER_ORDER = -50;

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        // Check rate limit for GET /{shortURL}
        if ("GET".equals(method) && !path.equals("/user") && !path.equals("/shorten")) {
            // Extract shortURL from path (remove leading slash)
            String shortURL = path.startsWith("/") ? path.substring(1) : path;
            return rateLimitService.checkGetRateLimit(shortURL)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return handleRateLimitExceeded(exchange);
                    }
                    return chain.filter(exchange);
                });
        }

        // Check rate limit for POST /shorten based on username
        if ("POST".equals(method) && "/shorten".equals(path)) {
            return ReactiveSecurityContextHolder.getContext()
                .cast(SecurityContext.class)
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMap(username -> rateLimitService.checkPostRateLimit(username)
                    .flatMap(allowed -> {
                        if (!allowed) {
                            return handleRateLimitExceeded(exchange);
                        }
                        return chain.filter(exchange);
                    }))
                .switchIfEmpty(chain.filter(exchange)); // If no auth, let security handle it
        }

        // No rate limiting for other paths
        return chain.filter(exchange);
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = new ErrorResponse("RATE_LIMIT_EXCEEDED", "Rate limit exceeded");

        try {
            String json = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}

