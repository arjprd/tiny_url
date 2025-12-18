package com.example.tinyurl.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${rate_limit.shorten.get.size:60}")
    private long getWindowSize;

    @Value("${rate_limit.shorten.get.capacity:10}")
    private long getCapacity;

    @Value("${rate_limit.shorten.post.size:60}")
    private long postWindowSize;

    @Value("${rate_limit.shorten.post.capacity:5}")
    private long postCapacity;

    /**
     * Checks rate limit for GET /{shortURL} based on shortURL parameter
     */
    public Mono<Boolean> checkGetRateLimit(String shortURL) {
        String key = "rate_limit:get:" + shortURL;
        return checkRateLimit(key, getWindowSize, getCapacity);
    }

    /**
     * Checks rate limit for POST /shorten based on username
     */
    public Mono<Boolean> checkPostRateLimit(String username) {
        String key = "rate_limit:post:" + username;
        return checkRateLimit(key, postWindowSize, postCapacity);
    }

    /**
     * Implements sliding window counter:
     * - On window start, set the key with TTL as window size
     * - Decrement value for each request
     * - If value is 0 or less, return false (rate limit exceeded)
     */
    private Mono<Boolean> checkRateLimit(String key, long windowSizeSeconds, long capacity) {
        ReactiveValueOperations<String, String> valueOps = redisTemplate.opsForValue();

        return valueOps.get(key)
            .flatMap(currentValue -> {
                try {
                    long currentCount = Long.parseLong(currentValue);
                    if (currentCount <= 0) {
                        // Rate limit exceeded - value is 0 or less
                        return Mono.just(false);
                    } else {
                        // Decrement value for this request
                        return valueOps.decrement(key)
                            .map(newCount -> newCount >= 0);
                    }
                } catch (NumberFormatException e) {
                    // Invalid value, reset window: set key with capacity and TTL
                    return valueOps.set(key, String.valueOf(capacity), Duration.ofSeconds(windowSizeSeconds))
                        .then(valueOps.decrement(key))
                        .then(Mono.just(true));
                }
            })
            .switchIfEmpty(
                // Key doesn't exist - window start: set key with capacity and TTL as window size
                // Then decrement for this request
                valueOps.set(key, String.valueOf(capacity), Duration.ofSeconds(windowSizeSeconds))
                    .then(valueOps.decrement(key))
                    .map(newCount -> newCount >= 0)
            );
    }
}

