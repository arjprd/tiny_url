package com.example.tinyurl.controller;

import com.example.tinyurl.entity.User;
import com.example.tinyurl.repository.UserRepository;
import com.example.tinyurl.service.TokenAuthenticationService;
import com.example.tinyurl.util.AESUtil;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.tinyurl.config.TestRedisConfig;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "auth.aes.secret.key=12345678901234567890123456789012",
    "auth.token.random.length=32",
    "auth.token.ttl=3600"
})
class UserControllerTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${auth.aes.secret.key}")
    private String aesSecretKey;

    @BeforeEach
    void setUp() {
        // Initialize WebTestClient with the server's base URL
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();

        // Clean up Redis token data
        redisTemplate.delete("token:*").block();
    }

    // ========== POST /user (createUser) Tests ==========

    @Test
    @DisplayName("Test POST /user - Success case: User created successfully")
    void testCreateUserSuccess() {
        String username = "newuser" + System.currentTimeMillis();
        String password = "password123";

        String requestBody = String.format("""
            {
                "username": "%s",
                "password": "%s"
            }
            """, username, password);

        webTestClient.post()
            .uri("/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("SUCCESS")
            .jsonPath("$.message").isEqualTo("User created successfully.");
    }

    @Test
    @DisplayName("Test POST /user - Error case: Invalid username (non-alphanumeric)")
    void testCreateUserWithInvalidUsername() {
        String requestBody = """
            {
                "username": "user@name",
                "password": "password123"
            }
            """;

        webTestClient.post()
            .uri("/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_USERNAME")
            .jsonPath("$.message").isEqualTo("Username must be alphanumeric");
    }

    @Test
    @DisplayName("Test POST /user - Error case: Duplicate username")
    void testCreateUserWithDuplicateUsername() {
        // Create user first
        String username = "duplicateuser" + System.currentTimeMillis();
        String password = "password123";

        // First request - should succeed
        String requestBody1 = String.format("""
            {
                "username": "%s",
                "password": "%s"
            }
            """, username, password);

        webTestClient.post()
            .uri("/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody1)
            .exchange()
            .expectStatus().isOk();

        // Second request with same username - should fail
        webTestClient.post()
            .uri("/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody1)
            .exchange()
            .expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("DUPLICATE_REQUEST")
            .jsonPath("$.message").isEqualTo("Username already exists");
    }

    // ========== POST /user/login Tests ==========

    @Test
    @DisplayName("Test POST /user/login - Success case: Login successful")
    void testLoginSuccess() {
        // Create user first
        String username = "loginuser" + System.currentTimeMillis();
        String password = "password123";

        createUserInDb(username, password);

        String requestBody = String.format("""
            {
                "username": "%s",
                "password": "%s"
            }
            """, username, password);

        webTestClient.post()
            .uri("/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.token").exists()
            .jsonPath("$.token").value(token -> {
                assertNotNull(token);
                String tokenStr = token.toString();
                assertTrue(tokenStr.contains("."), "Token should contain a dot separator");
            });
    }

    @Test
    @DisplayName("Test POST /user/login - Error case: Invalid username (non-alphanumeric)")
    void testLoginWithInvalidUsername() {
        String requestBody = """
            {
                "username": "user@name",
                "password": "password123"
            }
            """;

        webTestClient.post()
            .uri("/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_USERNAME")
            .jsonPath("$.message").isEqualTo("Username must be alphanumeric");
    }

    @Test
    @DisplayName("Test POST /user/login - Error case: Wrong username")
    void testLoginWithWrongUsername() {
        String requestBody = """
            {
                "username": "nonexistentuser",
                "password": "password123"
            }
            """;

        webTestClient.post()
            .uri("/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
            .jsonPath("$.message").isEqualTo("Invalid username or password");
    }

    @Test
    @DisplayName("Test POST /user/login - Error case: Wrong password")
    void testLoginWithWrongPassword() {
        // Create user first
        String username = "wrongpassuser" + System.currentTimeMillis();
        String correctPassword = "correctPassword123";
        String wrongPassword = "wrongPassword123";

        createUserInDb(username, correctPassword);

        String requestBody = String.format("""
            {
                "username": "%s",
                "password": "%s"
            }
            """, username, wrongPassword);

        webTestClient.post()
            .uri("/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
            .jsonPath("$.message").isEqualTo("Invalid username or password");
    }

    // ========== POST /user/logout Tests ==========

    @Test
    @DisplayName("Test POST /user/logout - Success case: Logout with me=true")
    void testLogoutSuccessWithMe() {
        // Create user and login to get token
        String username = "logoutmeuser_" + System.currentTimeMillis();
        String password = "password123";
        Long userId = createUserInDb(username, password);
        String bearerToken = createBearerToken(userId);

        String requestBody = """
            {
                "me": true,
                "all": false
            }
            """;

        webTestClient.post()
            .uri("/user/logout")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LOGGED_OUT")
            .jsonPath("$.message").isEqualTo("logged out successfully");
    }

    @Test
    @DisplayName("Test POST /user/logout - Success case: Logout with all=true")
    void testLogoutSuccessWithAll() {
        // Create user and login to get token
        String username = "logoutalluser_" + System.currentTimeMillis();
        String password = "password123";
        Long userId = createUserInDb(username, password);
        String bearerToken = createBearerToken(userId);

        String requestBody = """
            {
                "me": false,
                "all": true
            }
            """;

        webTestClient.post()
            .uri("/user/logout")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("LOGGED_OUT")
            .jsonPath("$.message").isEqualTo("logged out successfully");
    }

    @Test
    @DisplayName("Test POST /user/logout - Error case: Neither me nor all is true")
    void testLogoutWithNeitherMeNorAll() {
        // Create user and login to get token
        String username = "logoutfailuser_" + System.currentTimeMillis();
        String password = "password123";
        Long userId = createUserInDb(username, password);
        String bearerToken = createBearerToken(userId);

        String requestBody = """
            {
                "me": false,
                "all": false
            }
            """;

        webTestClient.post()
            .uri("/user/logout")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("BAD_REQUEST")
            .jsonPath("$.message").isEqualTo("Either 'me' or 'all' must be true");
    }

    @Test
    @DisplayName("Test POST /user/logout - Error case: Unauthorized (no token)")
    void testLogoutWithoutToken() {
        String requestBody = """
            {
                "me": true,
                "all": false
            }
            """;

        webTestClient.post()
            .uri("/user/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // ========== PATCH /user (changePassword) Tests ==========

    @Test
    @DisplayName("Test PATCH /user - Success case: Password changed successfully")
    void testChangePasswordSuccess() {
        // Create user and login to get token
        String username = "changepassuser_" + System.currentTimeMillis();
        String oldPassword = "oldPassword123";
        String newPassword = "newPassword456";
        Long userId = createUserInDb(username, oldPassword);
        String bearerToken = createBearerToken(userId);

        String requestBody = String.format("""
            {
                "oldPassword": "%s",
                "newPassword": "%s"
            }
            """, oldPassword, newPassword);

        webTestClient.patch()
            .uri("/user")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("PASSWORD_UPDATED")
            .jsonPath("$.message").isEqualTo("password updated successfully");
    }

    @Test
    @DisplayName("Test PATCH /user - Error case: Wrong old password")
    void testChangePasswordWithWrongOldPassword() {
        // Create user and login to get token
        String username = "wrongoldpassuser_" + System.currentTimeMillis();
        String correctPassword = "correctPassword123";
        String wrongOldPassword = "wrongOldPassword123";
        String newPassword = "newPassword456";
        Long userId = createUserInDb(username, correctPassword);
        String bearerToken = createBearerToken(userId);

        String requestBody = String.format("""
            {
                "oldPassword": "%s",
                "newPassword": "%s"
            }
            """, wrongOldPassword, newPassword);

        webTestClient.patch()
            .uri("/user")
            .header("Authorization", "Bearer " + bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
            .jsonPath("$.message").isEqualTo("Invalid old password");
    }

    @Test
    @DisplayName("Test PATCH /user - Error case: Unauthorized (no token)")
    void testChangePasswordWithoutToken() {
        String requestBody = """
            {
                "oldPassword": "oldPassword123",
                "newPassword": "newPassword456"
            }
            """;

        webTestClient.patch()
            .uri("/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // ========== Helper Methods ==========

    /**
     * Creates a user in the database and returns the user ID
     */
    private Long createUserInDb(String username, String password) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            // Check if user already exists
            var existingUser = userRepository.findByUsername(username);
            if (existingUser.isPresent()) {
                Long userId = existingUser.get().getId();
                transactionManager.commit(status);
                return userId;
            }

            // Create new user with properly hashed password
            String passwordHash = passwordEncoder.encode(password);
            User user = new User(username, passwordHash);
            User savedUser = userRepository.save(user);
            userRepository.flush();
            Long userId = savedUser.getId();
            transactionManager.commit(status);
            return userId;
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    /**
     * Creates a valid Bearer token for the given user ID
     */
    private String createBearerToken(Long userId) {
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
        return encryptedUserId + "." + randomString;
    }
}

