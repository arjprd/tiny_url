package com.example.tinyurl.filter;

import com.example.tinyurl.service.TokenAuthenticationService;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class BearerTokenAuthenticationFilter implements WebFilter, Ordered {

    private static final int FILTER_ORDER = -100; // Run before other security filters

    private final TokenAuthenticationService tokenAuthenticationService;

    public BearerTokenAuthenticationFilter(TokenAuthenticationService tokenAuthenticationService) {
        this.tokenAuthenticationService = tokenAuthenticationService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        
        // Process endpoints that require Bearer token authentication
        boolean requiresAuth = "/shorten".equals(path) 
            || "/user/logout".equals(path)
            || ("/user".equals(path) && "PATCH".equals(method))
            || (path.startsWith("/url/") && "GET".equals(method));
        
        if (!requiresAuth) {
            return chain.filter(exchange);
        }

        // Extract Bearer token from Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No token - let security handle it (will return 401)
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix
        if (token == null || token.isEmpty()) {
            return chain.filter(exchange);
        }

        // Verify token and set authentication in security context
        return tokenAuthenticationService.verifyToken(token)
            .flatMap(authentication -> {
                SecurityContext securityContext = new SecurityContextImpl(authentication);
                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
            })
            .switchIfEmpty(
                // If token invalid, let security handle it (will return 401)
                chain.filter(exchange)
            );
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}

