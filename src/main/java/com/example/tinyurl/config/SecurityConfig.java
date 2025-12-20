package com.example.tinyurl.config;

import com.example.tinyurl.filter.BearerTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter;
    private final CustomServerAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
                         CustomServerAuthenticationEntryPoint authenticationEntryPoint) {
        this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ReactiveUserDetailsPasswordService userDetailsPasswordService() {
        // No-op implementation since we're using token-based authentication
        return (user, newPassword) -> Mono.just(user);
    }

    @Bean
    public ReactiveAuthenticationManager authenticationManager() {
        // Minimal authentication manager - actual authentication is handled by BearerTokenAuthenticationFilter
        return authentication -> {
            // If already authenticated (by our custom filter), return as-is
            if (authentication.isAuthenticated()) {
                return Mono.just(authentication);
            }
            // Otherwise, return empty (authentication will be handled by our custom filter)
            return Mono.empty();
        };
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .authenticationManager(authenticationManager())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/user", "/user/login").permitAll()
                .pathMatchers("/api-docs/**", "/swagger-ui/**", "/docs").permitAll()
                .pathMatchers("/shorten").authenticated()
                .anyExchange().permitAll()
            )
            .addFilterBefore(bearerTokenAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
            )
            .build();
    }
}
