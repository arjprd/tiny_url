package com.example.tinyurl.controller;

import com.example.tinyurl.entity.User;
import com.example.tinyurl.repository.CustomUrlCodeRepository;
import com.example.tinyurl.repository.ShortUrlRepository;
import com.example.tinyurl.repository.UserRepository;
import com.example.tinyurl.service.TokenAuthenticationService;
import com.example.tinyurl.util.AESUtil;
import com.example.tinyurl.util.Base62Util;
import com.example.tinyurl.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import com.example.tinyurl.config.TestRedisConfig;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "app.host=http://localhost:8080",
    "auth.aes.secret.key=12345678901234567890123456789012"
})
class UrlControllerTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private CustomUrlCodeRepository customUrlCodeRepository;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${auth.aes.secret.key}")
    private String aesSecretKey;

    private Long userId;
    private String bearerToken;

    @BeforeEach
    void setUp() {
        // Initialize WebTestClient with the server's base URL
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();

        // Create and save a user in a separate committed transaction
        // Use unique username to avoid conflicts between test runs
        {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);
            try {
                // Use unique username with timestamp to avoid conflicts
                String username = "controllertestuser_" + System.currentTimeMillis();
                String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
                
                // Check if user already exists (in case of retry)
                var existingUser = userRepository.findByUsername(username);
                if (existingUser.isPresent()) {
                    userId = existingUser.get().getId();
                } else {
                    User user = new User(username, passwordHash);
                    User savedUser = userRepository.save(user);
                    userRepository.flush();
                    userId = savedUser.getId();
                }
                transactionManager.commit(status);
            } catch (Exception e) {
                transactionManager.rollback(status);
                throw e;
            }
        }

        assertNotNull(userId);

        // Create a valid Bearer token for authentication
        // Encrypt user ID
        String encryptedUserId = AESUtil.encrypt(String.valueOf(userId), aesSecretKey);
        
        // Generate random string
        String randomString = CryptoUtil.generateRandomString(32);
        
        // Store in Redis
        String redisKey = "token:" + userId;
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        hashOps.put(redisKey, randomString, "true").block();
        hashOps.expire(redisKey, Duration.ofSeconds(3600), Arrays.asList(randomString)).block();
        
        // Create token: <encrypted(user.id)>.<random_string>
        bearerToken = encryptedUserId + "." + randomString;
    }

    @Test
    @DisplayName("Test POST /shorten - Success case without customShortUrl and expiry")
    void testShortenUrlSuccessWithoutCustomAndExpiry() throws Exception {
        String requestBody = """
            {
                "url": "https://www.example.com/basic"
            }
            """;

        webTestClient.post()
            .uri("/shorten")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.shortUrl").exists()
            .jsonPath("$.shortUrl").value(url -> {
                assertNotNull(url);
                String shortUrl = url.toString();
                assertTrue(shortUrl.startsWith("http://localhost:8080/"));
                // Should have '_' prefix for Base62 encoded URLs
                String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
                assertTrue(code.startsWith("_"), "Base62 encoded URL should start with '_'");
            });
    }

    @Test
    @DisplayName("Test POST /shorten - Success case with customShortUrl")
    void testShortenUrlSuccessWithCustomShortUrl() throws Exception {
        String customShortUrl = "my-custom-link-123";
        String requestBody = String.format("""
            {
                "url": "https://www.example.com/custom",
                "shortUrl": "%s"
            }
            """, customShortUrl);

        webTestClient.post()
            .uri("/shorten")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.shortUrl").exists()
            .jsonPath("$.shortUrl").value(url -> {
                assertNotNull(url);
                String shortUrl = url.toString();
                assertTrue(shortUrl.startsWith("http://localhost:8080/"));
                // Should contain the custom short URL code
                String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
                assertEquals(customShortUrl, code, "Short URL should contain the custom code");
            });
    }

    @Test
    @DisplayName("Test POST /shorten - Success case with expiry")
    void testShortenUrlSuccessWithExpiry() throws Exception {
        // Set expiry to 1 hour from now
        OffsetDateTime expiry = OffsetDateTime.now().plusHours(1);
        String expiryString = expiry.toString();
        
        String requestBody = String.format("""
            {
                "url": "https://www.example.com/expiry",
                "expiry": "%s"
            }
            """, expiryString);

        String[] shortUrlHolder = new String[1];
        
        webTestClient.post()
            .uri("/shorten")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.shortUrl").exists()
            .jsonPath("$.shortUrl").value(url -> {
                assertNotNull(url);
                String shortUrl = url.toString();
                assertTrue(shortUrl.startsWith("http://localhost:8080/"));
                // Should have '_' prefix for Base62 encoded URLs
                String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
                assertTrue(code.startsWith("_"), "Base62 encoded URL should start with '_'");
                shortUrlHolder[0] = code;
            });

        // Verify expiry is stored correctly in database
        String encodedPart = shortUrlHolder[0].substring(1); // Remove '_' prefix
        long id = Base62Util.decode(encodedPart);
        
        var shortUrlOptional = shortUrlRepository.findById(id);
        assertTrue(shortUrlOptional.isPresent(), "ShortUrl should exist in database");
        
        var shortUrl = shortUrlOptional.get();
        assertNotNull(shortUrl.getExpiry(), "Expiry should not be null in database");
        
        // Verify expiry matches (with small tolerance for timing differences)
        OffsetDateTime dbExpiry = shortUrl.getExpiry();
        long secondsDifference = Math.abs(java.time.Duration.between(expiry, dbExpiry).getSeconds());
        assertTrue(secondsDifference < 5, 
            String.format("Expiry in database should match request expiry (difference: %d seconds)", secondsDifference));
    }

    @Test
    @DisplayName("Test POST /shorten - Success case with both customShortUrl and expiry")
    void testShortenUrlSuccessWithCustomShortUrlAndExpiry() throws Exception {
        String customShortUrl = "my-custom-expiry-link";
        OffsetDateTime expiry = OffsetDateTime.now().plusHours(2);
        String expiryString = expiry.toString();
        
        String requestBody = String.format("""
            {
                "url": "https://www.example.com/custom-expiry",
                "shortUrl": "%s",
                "expiry": "%s"
            }
            """, customShortUrl, expiryString);

        webTestClient.post()
            .uri("/shorten")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.shortUrl").exists()
            .jsonPath("$.shortUrl").value(url -> {
                assertNotNull(url);
                String shortUrl = url.toString();
                assertTrue(shortUrl.startsWith("http://localhost:8080/"));
                // Should contain the custom short URL code
                String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
                assertEquals(customShortUrl, code, "Short URL should contain the custom code");
            });

        // Verify expiry is stored correctly in database via custom_url_code
        var customCodeOptional = customUrlCodeRepository.findByCode(customShortUrl);
        assertTrue(customCodeOptional.isPresent(), "CustomUrlCode should exist in database");
        
        var customCode = customCodeOptional.get();
        var shortUrl = customCode.getUrl();
        assertNotNull(shortUrl.getExpiry(), "Expiry should not be null in database");
        
        // Verify expiry matches (with small tolerance for timing differences)
        OffsetDateTime dbExpiry = shortUrl.getExpiry();
        long secondsDifference = Math.abs(java.time.Duration.between(expiry, dbExpiry).getSeconds());
        assertTrue(secondsDifference < 5, 
            String.format("Expiry in database should match request expiry (difference: %d seconds)", secondsDifference));
    }

    @Test
    @DisplayName("Test POST /shorten - Error case: Invalid URL")
    void testShortenUrlWithInvalidUrl() throws Exception {
        String requestBody = """
            {
                "url": "not-a-valid-url"
            }
            """;

        webTestClient.post()
            .uri("/shorten")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_URL")
            .jsonPath("$.message").isEqualTo("Provided URL is invalid");
    }

    @Test
    @DisplayName("Test POST /shorten - Error case: Duplicate custom short URL")
    void testShortenUrlWithDuplicateCustomShortUrl() throws Exception {
        String customShortUrl = "duplicate-custom-link";
        String requestBody1 = String.format("""
            {
                "url": "https://www.example.com/first",
                "shortUrl": "%s"
            }
            """, customShortUrl);

        // First request - should succeed
        webTestClient.post()
            .uri("/shorten")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody1)
            .exchange()
            .expectStatus().isOk();

        // Second request with same custom short URL - should fail
        String requestBody2 = String.format("""
            {
                "url": "https://www.example.com/second",
                "shortUrl": "%s"
            }
            """, customShortUrl);

        webTestClient.post()
            .uri("/shorten")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody2)
            .exchange()
            .expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("DUPLICATE_REQUEST")
            .jsonPath("$.message").isEqualTo("Custom short URL already exists");
    }

    @Test
    @DisplayName("Test POST /shorten - Error case: Unauthorized (no token)")
    void testShortenUrlWithoutToken() throws Exception {
        String requestBody = """
            {
                "url": "https://www.example.com/unauthorized"
            }
            """;

        webTestClient.post()
            .uri("/shorten")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isUnauthorized();
    }
}

