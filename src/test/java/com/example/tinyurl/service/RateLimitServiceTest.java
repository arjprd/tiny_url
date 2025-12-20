package com.example.tinyurl.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.example.tinyurl.config.TestRedisConfig;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "rate_limit.shorten.get.size=60",  // 60 seconds window (enough for test scenario)
    "rate_limit.shorten.get.capacity=10",
    "rate_limit.shorten.post.size=60",  // 60 seconds window (enough for test scenario)
    "rate_limit.shorten.post.capacity=5"
})
class RateLimitServiceTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    @DisplayName("Test checkGetRateLimit: invoke repeatedly with gap of 3 seconds, should return true for first 10 invocations, then false, then true from 21st invocation")
    void testCheckGetRateLimitWithRepeatedInvocations() throws InterruptedException {
        String shortUrl = "testShortUrl123";

        // First 10 invocations should return true (within capacity)
        for (int i = 1; i <= 10; i++) {
            Mono<Boolean> result = rateLimitService.checkGetRateLimit(shortUrl);
            StepVerifier.create(result)
                    .expectNext(true)
                    .verifyComplete();
            
            if (i < 10) {
                Thread.sleep(3000); // 3 second gap between invocations
            }
        }

        // 11th invocation should return false (capacity exceeded)
        Mono<Boolean> result11 = rateLimitService.checkGetRateLimit(shortUrl);
        StepVerifier.create(result11)
                .expectNext(false)
                .verifyComplete();

        // Wait for the window to expire (60 seconds window, we've used ~27 seconds so far)
        // Need to wait until the window expires (60 seconds total)
        // After 10 invocations with 3 second gaps = ~27 seconds, wait additional ~35 seconds
        Thread.sleep(35000); // Wait to ensure window expired

        // 21st invocation (after window expiry) should return true (new window started)
        Mono<Boolean> result21 = rateLimitService.checkGetRateLimit(shortUrl);
        StepVerifier.create(result21)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Test checkPostRateLimit: invoke repeatedly with gap of 5 seconds, should return true for first 5 invocations, then false, then true from 11th invocation")
    void testCheckPostRateLimitWithRepeatedInvocations() throws InterruptedException {
        String username = "testuser123";

        // First 5 invocations should return true (within capacity)
        for (int i = 1; i <= 5; i++) {
            Mono<Boolean> result = rateLimitService.checkPostRateLimit(username);
            StepVerifier.create(result)
                    .expectNext(true)
                    .verifyComplete();
            
            if (i < 5) {
                Thread.sleep(5000); // 5 second gap between invocations
            }
        }

        // 6th invocation should return false (capacity exceeded)
        Mono<Boolean> result6 = rateLimitService.checkPostRateLimit(username);
        StepVerifier.create(result6)
                .expectNext(false)
                .verifyComplete();

        // Wait for the window to expire (60 seconds window, we've used ~20 seconds so far)
        // Need to wait until the window expires (60 seconds total)
        // After 5 invocations with 5 second gaps = ~20 seconds, wait additional ~45 seconds
        Thread.sleep(45000); // Wait to ensure window expired

        // 11th invocation (after window expiry) should return true (new window started)
        Mono<Boolean> result11 = rateLimitService.checkPostRateLimit(username);
        StepVerifier.create(result11)
                .expectNext(true)
                .verifyComplete();
    }
}

