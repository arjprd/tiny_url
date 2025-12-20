package com.example.tinyurl.service;

import com.example.tinyurl.util.AESUtil;
import com.example.tinyurl.util.CustomAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.example.tinyurl.config.TestRedisConfig;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "auth.aes.secret.key=12345678901234567890123456789012"
})
class TokenAuthenticationServiceTest {

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${auth.aes.secret.key}")
    private String aesSecretKey;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        // Delete the specific field from the hash
        // hashOps.remove("token:123", "abcdef").block();
        // Also delete the entire key to ensure clean state
        redisTemplate.delete("token:123").block();
    }

    @Test
    @DisplayName("Test verifyToken: create hash set in Redis, encrypt user ID, verify token and check CustomAuthentication properties")
    void testVerifyTokenWithValidToken() {
        // Step 1: Create hash set in Redis with key 'token:123', field 'abcdef', value 'true' with TTL of 60 sec
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        String redisKey = "token:123";
        String field = "abcdef";
        String value = "true";
        
        // Set the hash field
        Mono<Boolean> setHash = hashOps.put(redisKey, field, value)
            .then(redisTemplate.expire(redisKey, Duration.ofSeconds(60)))
            .then(Mono.just(true));
        
        StepVerifier.create(setHash)
            .expectNext(true)
            .verifyComplete();

        // Step 2: Encrypt user ID "123"
        String userId = "123";
        String encryptedUserId = AESUtil.encrypt(userId, aesSecretKey);
        assertNotNull(encryptedUserId, "Encrypted user ID should not be null");

        // Step 3: Invoke verifyToken with string encrypted(123).'abcdef'
        String token = encryptedUserId + "." + field;
        Mono<Authentication> authMono = tokenAuthenticationService.verifyToken(token);

        // Step 4 & 5: Cast to CustomAuthentication and assert getName, getCredentials, getUserId
        StepVerifier.create(authMono)
            .assertNext(authentication -> {
                assertNotNull(authentication, "Authentication should not be null");
                assertTrue(authentication instanceof CustomAuthentication, 
                    "Authentication should be instance of CustomAuthentication");
                
                CustomAuthentication customAuth = (CustomAuthentication) authentication;
                
                // Assert getName()
                String name = customAuth.getName();
                assertEquals(userId, name, "getName() should return the user ID as string");
                
                // Assert getCredentials()
                String credentials = customAuth.getCredentials();
                assertEquals(field, credentials, "getCredentials() should return the random token");
                
                // Assert getUserId()
                Long userIdLong = customAuth.getUserId();
                assertEquals(Long.parseLong(userId), userIdLong, 
                    "getUserId() should return the user ID as Long");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test verifyToken: encrypt user ID, invoke with token but no Redis entry, should return empty")
    void testVerifyTokenWithMissingRedisEntry() {
        // Step 1: Encrypt user ID "123"
        String userId = "123";
        String encryptedUserId = AESUtil.encrypt(userId, aesSecretKey);
        assertNotNull(encryptedUserId, "Encrypted user ID should not be null");

        // Step 2: Invoke verifyToken with string encrypted(123).'abcdef'
        // Note: We do NOT create the Redis hash entry, so it should return empty
        String field = "abcdef";
        String token = encryptedUserId + "." + field;
        Mono<Authentication> authMono = tokenAuthenticationService.verifyToken(token);

        // Step 3: Return should be empty (Mono.empty())
        StepVerifier.create(authMono)
            .verifyComplete(); // Expects empty Mono (no values emitted)
    }

    @Test
    @DisplayName("Test verifyToken: create hash set in Redis, encrypt user ID, invoke with different field name, should return empty")
    void testVerifyTokenWithWrongFieldName() {
        // Step 1: Create hash set in Redis with key 'token:123', field 'abcdef', value 'true' with TTL of 60 sec
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        String redisKey = "token:123";
        String field = "abcdef";
        String value = "true";
        
        // Set the hash field
        Mono<Boolean> setHash = hashOps.put(redisKey, field, value)
            .then(redisTemplate.expire(redisKey, Duration.ofSeconds(60)))
            .then(Mono.just(true));
        
        StepVerifier.create(setHash)
            .expectNext(true)
            .verifyComplete();

        // Step 2: Encrypt user ID "123"
        String userId = "123";
        String encryptedUserId = AESUtil.encrypt(userId, aesSecretKey);
        assertNotNull(encryptedUserId, "Encrypted user ID should not be null");

        // Step 3: Invoke verifyToken with string encrypted(123).'xasdert'
        // Note: The field 'xasdert' doesn't exist in Redis (only 'abcdef' exists)
        String wrongField = "xasdert";
        String token = encryptedUserId + "." + wrongField;
        Mono<Authentication> authMono = tokenAuthenticationService.verifyToken(token);

        // Step 4: The response should be empty (Mono.empty())
        StepVerifier.create(authMono)
            .verifyComplete(); // Expects empty Mono (no values emitted) because field doesn't exist
    }
}

