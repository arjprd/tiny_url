package com.example.tinyurl.service;

import com.example.tinyurl.util.AESUtil;
import com.example.tinyurl.util.CustomAuthentication;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
public class TokenAuthenticationService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${auth.aes.secret.key}")
    private String aesSecretKey;

    public TokenAuthenticationService(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Verifies Bearer token and returns authentication with userId
     * Token verification logic:
     * 1. Split token by '.'
     * 2. Perform AES decrypt of first portion as user_id
     * 3. Hash get from redis for key token:<user_id>, field_name as <second portion of split>
     * 4. If the field_name exists token is valid
     */
    public Mono<Authentication> verifyToken(String token) {
        if (token == null || token.isEmpty()) {
            return Mono.empty();
        }

        try {
            // Split token by '.'
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return Mono.empty();
            }

            String encryptedUserId = parts[0];
            String randomString = parts[1];

            // Perform AES decrypt of first portion as user_id
            String userId;
            try {
                userId = AESUtil.decrypt(encryptedUserId, aesSecretKey);
            } catch (Exception e) {
                return Mono.empty();
            }

            // Hash get from redis for key token:<user_id>, field_name as <second portion of split>
            String redisKey = "token:" + userId;
            ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

            return hashOps.get(redisKey, randomString)
                .flatMap(value -> {
                    if (value != null && "true".equals(value)) {
                        Authentication auth = new CustomAuthentication(userId, randomString);
                        return Mono.just(auth);
                    } else {
                        // Token field doesn't exist - invalid token
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.empty());
        } catch (Exception e) {
            return Mono.empty();
        }
    }
}

