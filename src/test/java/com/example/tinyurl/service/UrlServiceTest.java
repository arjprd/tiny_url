package com.example.tinyurl.service;

import com.example.tinyurl.entity.User;
import com.example.tinyurl.repository.ShortUrlRepository;
import com.example.tinyurl.repository.UserRepository;
import com.example.tinyurl.util.Base62Util;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import com.example.tinyurl.config.TestRedisConfig;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "app.host=http://localhost:8080"
})
@Slf4j
class UrlServiceTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        // Clean up Redis cache and locks if needed
        // Using @Transactional ensures database cleanup, Redis cleanup handled per test if needed
    }

    @Test
    @DisplayName("Test shortenUrl with invalid URL: should return error with status 400, code INVALID_URL")
    void testShortenUrlWithInvalidUrl() {
        Long userId = 1L;
        String invalidUrl = "not-a-valid-url";

        // Invoke shortenUrl with invalid URL
        var resultMono = urlService.shortenUrl(invalidUrl, null, null, userId);

        // Assert the result
        StepVerifier.create(resultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for invalid URL");

                // getStatus() should be 400
                assertEquals(HttpStatus.BAD_REQUEST, result.getStatus(), 
                    "Status should be BAD_REQUEST (400) for invalid URL");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for invalid URL");

                // getError().getCode() should be INVALID_URL
                assertEquals("INVALID_URL", result.getError().getCode(), 
                    "Error code should be INVALID_URL");

                // getError().getMessage() should be "Provided URL is invalid"
                assertEquals("Provided URL is invalid", result.getError().getMessage(), 
                    "Error message should be 'Provided URL is invalid'");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test shortenUrl with duplicate URL: first call succeeds, second call returns DUPLICATE_REQUEST")
    void testShortenUrlWithDuplicateUrl() {
        // Create and save a user in a separate committed transaction
        // This ensures the User is visible to other threads when shortenUrl runs
        Long userId;
        {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                String username = "urltestuser";
                String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
                User user = new User(username, passwordHash);
                User savedUser = userRepository.save(user);
                userRepository.flush();
                userId = savedUser.getId();
                transactionManager.commit(status);
            } catch (Exception e) {
                transactionManager.rollback(status);
                throw e;
            }
        }

        assertNotNull(userId);

        String validUrl = "https://www.example.com/test";

        // First invocation - should succeed
        var firstResultMono = urlService.shortenUrl(validUrl, null, null, userId);

        StepVerifier.create(firstResultMono)
            .assertNext(result -> {
                // getResponse() should be a non-empty string (shortUrl)
                if (result.getError() != null) {
                    log.error("Error in first call: {} - {}", result.getError().getCode(), result.getError().getMessage());
                }
                assertNotNull(result.getResponse(), "Response should not be null for first call");
                assertNotNull(result.getResponse().getShortUrl(), "Short URL should not be null");
                assertFalse(result.getResponse().getShortUrl().isEmpty(), 
                    "Short URL should not be empty");

                // getStatus() should be 200
                assertEquals(HttpStatus.OK, result.getStatus(), 
                    "Status should be OK (200) for first call");

                // getError() should be null
                assertNull(result.getError(), "Error should be null for first call");
            })
            .verifyComplete();

        // Second invocation with same URL - should return DUPLICATE_REQUEST
        var secondResultMono = urlService.shortenUrl(validUrl, null, null, userId);

        StepVerifier.create(secondResultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for duplicate URL");

                // getStatus() should be 409
                assertEquals(HttpStatus.CONFLICT, result.getStatus(), 
                    "Status should be CONFLICT (409) for duplicate URL");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for duplicate URL");

                // getError().getCode() should be DUPLICATE_REQUEST
                assertEquals("DUPLICATE_REQUEST", result.getError().getCode(), 
                    "Error code should be DUPLICATE_REQUEST");

                // getError().getMessage() should be "A short URL exists for the long URL"
                assertEquals("A short URL exists for the long URL", result.getError().getMessage(), 
                    "Error message should be 'A short URL exists for the long URL'");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test getLongUrl: create user, shorten URL, then get long URL back")
    void testGetLongUrlBasic() {
        // Create and save a user in a separate committed transaction
        Long userId;
        {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                String username = "getlonguser";
                String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
                User user = new User(username, passwordHash);
                User savedUser = userRepository.save(user);
                userRepository.flush();
                userId = savedUser.getId();
                transactionManager.commit(status);
            } catch (Exception e) {
                transactionManager.rollback(status);
                throw e;
            }
        }

        assertNotNull(userId);

        String longUrl = "https://www.example.com/getlongtest";

        // Shorten the URL
        var shortenResultMono = urlService.shortenUrl(longUrl, null, null, userId);

        String[] shortUrlHolder = new String[1];
        StepVerifier.create(shortenResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertNotNull(result.getResponse().getShortUrl());
                shortUrlHolder[0] = result.getResponse().getShortUrl();
            })
            .verifyComplete();

        String shortUrlEncoded = shortUrlHolder[0];
        // Extract the base62 encoded part from "http://localhost:8080/{encoded}"
        String encodedPart = shortUrlEncoded.substring(shortUrlEncoded.lastIndexOf('/') + 1);

        // Clear Redis cache to ensure we test the full flow
        redisTemplate.delete("short:" + encodedPart).block();
        redisTemplate.delete("lock:short:" + encodedPart).block();

        // Get long URL
        var getLongUrlResultMono = urlService.getLongUrl(encodedPart);

        StepVerifier.create(getLongUrlResultMono)
            .assertNext(result -> {
                // getLongUrl() should return the actual long URL
                assertNotNull(result.getLongUrl(), "Long URL should not be null");
                assertEquals(longUrl, result.getLongUrl(), 
                    "Returned long URL should match the original long URL");

                // getError() should be null
                assertNull(result.getError(), "Error should be null for valid short URL");

                // getStatus() should be 301 (MOVED_PERMANENTLY)
                assertEquals(HttpStatus.MOVED_PERMANENTLY, result.getStatus(), 
                    "Status should be MOVED_PERMANENTLY (301)");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test getLongUrl concurrent calls: 10 concurrent calls should result in only one DB call")
    void testGetLongUrlConcurrent() throws InterruptedException {
        // Create and save a user in a separate committed transaction
        Long userId;
        {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                String username = "concurrentuser";
                String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
                User user = new User(username, passwordHash);
                User savedUser = userRepository.save(user);
                userRepository.flush();
                userId = savedUser.getId();
                transactionManager.commit(status);
            } catch (Exception e) {
                transactionManager.rollback(status);
                throw e;
            }
        }

        assertNotNull(userId);

        String longUrl = "https://www.example.com/concurrenttest";

        // Shorten the URL
        var shortenResultMono = urlService.shortenUrl(longUrl, null, null, userId);

        String[] shortUrlHolder = new String[1];
        StepVerifier.create(shortenResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertNotNull(result.getResponse().getShortUrl());
                shortUrlHolder[0] = result.getResponse().getShortUrl();
            })
            .verifyComplete();

        String shortUrlEncoded = shortUrlHolder[0];
        // Extract the base62 encoded part
        String encodedPart = shortUrlEncoded.substring(shortUrlEncoded.lastIndexOf('/') + 1);

        // Clear Redis cache to ensure we test the lock mechanism
        redisTemplate.delete("short:" + encodedPart).block();
        redisTemplate.delete("lock:short:" + encodedPart).block();

        // Create a wrapper to verify concurrent calls
        // The lock mechanism should ensure only one DB call is made
        int numConcurrentCalls = 10;
        CountDownLatch latch = new CountDownLatch(numConcurrentCalls);
        List<String> results = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        // Make concurrent calls
        for (int i = 0; i < numConcurrentCalls; i++) {
            new Thread(() -> {
                try {
                    var resultMono = urlService.getLongUrl(encodedPart);
                    String[] longUrlHolder = new String[1];
                    StepVerifier.create(resultMono)
                        .assertNext(result -> {
                            assertNotNull(result.getLongUrl());
                            assertEquals(longUrl, result.getLongUrl());
                            longUrlHolder[0] = result.getLongUrl();
                        })
                        .expectComplete()
                        .verify();
                    synchronized (results) {
                        results.add(longUrlHolder[0]);
                    }
                } catch (Throwable e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all calls to complete (with timeout)
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "All concurrent calls should complete within timeout");

        // Verify all calls succeeded
        assertEquals(0, errors.size(), "No errors should occur during concurrent calls");
        assertEquals(numConcurrentCalls, results.size(), 
            "All concurrent calls should return results");

        // Verify all results are the same (indicating cache is working)
        for (String result : results) {
            assertEquals(longUrl, result, "All results should return the same long URL");
        }

        // Verify cache was populated (subsequent calls should be faster)
        // We can't directly count DB calls without a spy, but we can verify
        // that the cache is working by checking Redis
        String cachedValue = redisTemplate.opsForValue().get("short:" + encodedPart).block();
        assertNotNull(cachedValue, "Cache should be populated after first call");
        assertEquals(longUrl, cachedValue, "Cached value should match the long URL");
    }

    @Test
    @DisplayName("Test getLongUrl with invalid short URL: should return NO_RECORD error")
    void testGetLongUrlWithInvalidShortUrl() {
        // Generate a random base62 encoded string that doesn't exist
        // Use a large number that's unlikely to exist
        String randomEncoded = Base62Util.encode(999999999L);

        // Invoke getLongUrl with random URL
        var resultMono = urlService.getLongUrl(randomEncoded);

        StepVerifier.create(resultMono)
            .assertNext(result -> {
                // getLongUrl() should be null
                assertNull(result.getLongUrl(), "Long URL should be null for invalid short URL");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for invalid short URL");

                // getError().getCode() should be NO_RECORD
                assertEquals("NO_RECORD", result.getError().getCode(), 
                    "Error code should be NO_RECORD");

                // getError().getMessage() should be "A long URL does exists for the short URL"
                assertEquals("A long URL does not exist for the short URL", result.getError().getMessage(), 
                    "Error message should be 'A long URL does exists for the short URL'");

                // getStatus() should be 404 (NOT_FOUND)
                assertEquals(HttpStatus.NOT_FOUND, result.getStatus(), 
                    "Status should be NOT_FOUND (404)");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test shortenUrl with customShortUrl: response should contain the customShortUrl")
    void testShortenUrlWithCustomShortUrl() {
        // Create and save a user in a separate committed transaction
        Long userId;
        {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                String username = "customurluser";
                String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
                User user = new User(username, passwordHash);
                User savedUser = userRepository.save(user);
                userRepository.flush();
                userId = savedUser.getId();
                transactionManager.commit(status);
            } catch (Exception e) {
                transactionManager.rollback(status);
                throw e;
            }
        }

        assertNotNull(userId);

        String longUrl = "https://www.example.com/customtest";
        String customShortUrl = "my-custom-link-123";

        // Invoke shortenUrl with custom short URL
        var resultMono = urlService.shortenUrl(longUrl, customShortUrl, null, userId);

        StepVerifier.create(resultMono)
            .assertNext(result -> {
                // getResponse() should not be null
                assertNotNull(result.getResponse(), "Response should not be null for custom short URL");

                // getResponse().getShortUrl() should contain the custom short URL
                assertNotNull(result.getResponse().getShortUrl(), "Short URL should not be null");
                String responseShortUrl = result.getResponse().getShortUrl();
                
                // Extract the code part from "http://localhost:8080/{code}"
                String codePart = responseShortUrl.substring(responseShortUrl.lastIndexOf('/') + 1);
                assertEquals(customShortUrl, codePart, 
                    "Response short URL should contain the custom short URL code");

                // getStatus() should be 200
                assertEquals(HttpStatus.OK, result.getStatus(), 
                    "Status should be OK (200) for custom short URL");

                // getError() should be null
                assertNull(result.getError(), "Error should be null for custom short URL");
            })
            .verifyComplete();

        // Verify we can retrieve the long URL using the custom short URL
        var getLongUrlResultMono = urlService.getLongUrl(customShortUrl);

        StepVerifier.create(getLongUrlResultMono)
            .assertNext(result -> {
                assertNotNull(result.getLongUrl(), "Long URL should not be null");
                assertEquals(longUrl, result.getLongUrl(), 
                    "Returned long URL should match the original long URL");
                assertEquals(HttpStatus.MOVED_PERMANENTLY, result.getStatus());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test expiry functionality: shortenUrl with expiry, verify getLongUrl works, wait for expiry, then verify getLongUrl returns NOT_FOUND")
    void testShortenUrlWithExpiry() throws InterruptedException {
        // Create and save a user in a separate committed transaction
        Long userId;
        {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                String username = "expiryuser";
                String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
                User user = new User(username, passwordHash);
                User savedUser = userRepository.save(user);
                userRepository.flush();
                userId = savedUser.getId();
                transactionManager.commit(status);
            } catch (Exception e) {
                transactionManager.rollback(status);
                throw e;
            }
        }

        assertNotNull(userId);

        String longUrl = "https://www.example.com/expirytest";
        
        // Set expiry to current timestamp + 60 seconds
        OffsetDateTime expiry = OffsetDateTime.now().plusSeconds(60);

        // Invoke shortenUrl with expiry
        var shortenResultMono = urlService.shortenUrl(longUrl, null, expiry, userId);

        String[] shortUrlHolder = new String[1];
        StepVerifier.create(shortenResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse(), "Response should not be null");
                assertNotNull(result.getResponse().getShortUrl(), "Short URL should not be null");
                assertEquals(HttpStatus.OK, result.getStatus());
                assertNull(result.getError());
                shortUrlHolder[0] = result.getResponse().getShortUrl();
            })
            .verifyComplete();

        String shortUrlEncoded = shortUrlHolder[0];
        // Extract the base62 encoded part (remove '_' prefix and host)
        String encodedPart = shortUrlEncoded.substring(shortUrlEncoded.lastIndexOf('/') + 1);

        // Clear Redis cache to ensure we test the expiry check
        redisTemplate.delete("short:" + encodedPart).block();
        redisTemplate.delete("lock:short:" + encodedPart).block();

        // Verify getLongUrl works initially (before expiry)
        var getLongUrlResultMono1 = urlService.getLongUrl(encodedPart);

        StepVerifier.create(getLongUrlResultMono1)
            .assertNext(result -> {
                // getLongUrl() should return the actual long URL (not expired yet)
                assertNotNull(result.getLongUrl(), "Long URL should not be null before expiry");
                assertEquals(longUrl, result.getLongUrl(), 
                    "Returned long URL should match the original long URL");
                assertNull(result.getError(), "Error should be null before expiry");
                assertEquals(HttpStatus.MOVED_PERMANENTLY, result.getStatus());
            })
            .verifyComplete();

        // Wait for 65 seconds to ensure expiry has passed
        log.info("Waiting for 65 seconds for URL to expire...");
        Thread.sleep(65000); // 65 seconds
        log.info("Wait completed, testing expired URL...");

        // Clear Redis cache again to force DB lookup (which will check expiry)
        redisTemplate.delete("short:" + encodedPart).block();
        redisTemplate.delete("lock:short:" + encodedPart).block();

        // Verify getLongUrl returns NOT_FOUND after expiry
        var getLongUrlResultMono2 = urlService.getLongUrl(encodedPart);

        StepVerifier.create(getLongUrlResultMono2)
            .assertNext(result -> {
                // getLongUrl() should be null (expired)
                assertNull(result.getLongUrl(), "Long URL should be null after expiry");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for expired URL");

                // getError().getCode() should be NO_RECORD
                assertEquals("NO_RECORD", result.getError().getCode(), 
                    "Error code should be NO_RECORD for expired URL");

                // getError().getMessage() should be "A long URL does not exist for the short URL"
                assertEquals("A long URL does not exist for the short URL", result.getError().getMessage(), 
                    "Error message should indicate URL not found");

                // getStatus() should be 404 (NOT_FOUND)
                assertEquals(HttpStatus.NOT_FOUND, result.getStatus(), 
                    "Status should be NOT_FOUND (404) for expired URL");
            })
            .verifyComplete();
    }
}

