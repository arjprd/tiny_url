package com.example.tinyurl.service;

import com.example.tinyurl.entity.User;
import com.example.tinyurl.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import com.example.tinyurl.config.TestRedisConfig;
import com.example.tinyurl.util.CustomAuthentication;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
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
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenAuthenticationService tokenAuthenticationService;

    @Autowired
    @Qualifier("reactiveStringRedisTemplate")
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // Clean up Redis token data if needed
        // Using @Transactional ensures database cleanup
    }

    @Test
    @DisplayName("Test createUser with non-alphanumeric username: should return error with status 400, code INVALID_USERNAME")
    void testCreateUserWithNonAlphanumericUsername() {
        String nonAlphanumericUsername = "user@name";
        String password = "password123";

        // Invoke createUser with non-alphanumeric username
        var resultMono = userService.createUser(nonAlphanumericUsername, password);

        // Assert the result
        StepVerifier.create(resultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for non-alphanumeric username");

                // getStatus() should be 400
                assertEquals(HttpStatus.BAD_REQUEST, result.getStatus(), 
                    "Status should be BAD_REQUEST (400) for non-alphanumeric username");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for non-alphanumeric username");

                // getError().getCode() should be INVALID_USERNAME
                assertEquals("INVALID_USERNAME", result.getError().getCode(), 
                    "Error code should be INVALID_USERNAME");

                // getError().getMessage() should be "Username must be alphanumeric"
                assertEquals("Username must be alphanumeric", result.getError().getMessage(), 
                    "Error message should be 'Username must be alphanumeric'");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test createUser twice for same username: first call succeeds, second call returns DUPLICATE_REQUEST")
    void testCreateUserWithDuplicateUsername() {
        String username = "duplicateuser";
        String password = "password123";

        // First invocation - should succeed
        var firstResultMono = userService.createUser(username, password);

        StepVerifier.create(firstResultMono)
            .assertNext(result -> {
                // getResponse() should not be null
                assertNotNull(result.getResponse(), "Response should not be null for first call");
                assertEquals("SUCCESS", result.getResponse().getCode(), 
                    "Response code should be SUCCESS");
                assertEquals("User created successfully.", result.getResponse().getMessage(), 
                    "Response message should be 'User created successfully.'");

                // getStatus() should be 200
                assertEquals(HttpStatus.OK, result.getStatus(), 
                    "Status should be OK (200) for first call");

                // getError() should be null
                assertNull(result.getError(), "Error should be null for first call");
            })
            .verifyComplete();

        // Second invocation with same username - should return DUPLICATE_REQUEST
        var secondResultMono = userService.createUser(username, password);

        StepVerifier.create(secondResultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for duplicate username");

                // getStatus() should be 409
                assertEquals(HttpStatus.CONFLICT, result.getStatus(), 
                    "Status should be CONFLICT (409) for duplicate username");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for duplicate username");

                // getError().getCode() should be DUPLICATE_REQUEST
                assertEquals("DUPLICATE_REQUEST", result.getError().getCode(), 
                    "Error code should be DUPLICATE_REQUEST");

                // getError().getMessage() should be "Username already exists"
                assertEquals("Username already exists", result.getError().getMessage(), 
                    "Error message should be 'Username already exists'");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test login with non-alphanumeric username: should return error with status 400, code INVALID_USERNAME")
    void testLoginWithNonAlphanumericUsername() {
        String nonAlphanumericUsername = "user@name";
        String password = "password123";

        // Invoke login with non-alphanumeric username
        var resultMono = userService.login(nonAlphanumericUsername, password);

        // Assert the result
        StepVerifier.create(resultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for non-alphanumeric username");

                // getStatus() should be 400
                assertEquals(HttpStatus.BAD_REQUEST, result.getStatus(), 
                    "Status should be BAD_REQUEST (400) for non-alphanumeric username");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for non-alphanumeric username");

                // getError().getCode() should be INVALID_USERNAME
                assertEquals("INVALID_USERNAME", result.getError().getCode(), 
                    "Error code should be INVALID_USERNAME");

                // getError().getMessage() should be "Username must be alphanumeric"
                assertEquals("Username must be alphanumeric", result.getError().getMessage(), 
                    "Error message should be 'Username must be alphanumeric'");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test login with wrong password: should return error with status 401, code UNAUTHORIZED")
    void testLoginWithWrongPassword() {
        // Create a user first
        String username = "wrongpassuser";
        String correctPassword = "correctPassword123";
        String wrongPassword = "wrongPassword123";

        // Create user
        var createResultMono = userService.createUser(username, correctPassword);
        StepVerifier.create(createResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals(HttpStatus.OK, result.getStatus());
            })
            .verifyComplete();

        // Try to login with wrong password
        var loginResultMono = userService.login(username, wrongPassword);

        StepVerifier.create(loginResultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for wrong password");

                // getStatus() should be 401
                assertEquals(HttpStatus.UNAUTHORIZED, result.getStatus(), 
                    "Status should be UNAUTHORIZED (401) for wrong password");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for wrong password");

                // getError().getCode() should be UNAUTHORIZED
                assertEquals("UNAUTHORIZED", result.getError().getCode(), 
                    "Error code should be UNAUTHORIZED");

                // getError().getMessage() should be "Invalid username or password"
                assertEquals("Invalid username or password", result.getError().getMessage(), 
                    "Error message should be 'Invalid username or password'");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test login with wrong username: should return error with status 401, code UNAUTHORIZED")
    void testLoginWithWrongUsername() {
        String wrongUsername = "nonexistentuser";
        String password = "password123";

        // Try to login with non-existent username
        var loginResultMono = userService.login(wrongUsername, password);

        StepVerifier.create(loginResultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for wrong username");

                // getStatus() should be 401
                assertEquals(HttpStatus.UNAUTHORIZED, result.getStatus(), 
                    "Status should be UNAUTHORIZED (401) for wrong username");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for wrong username");

                // getError().getCode() should be UNAUTHORIZED
                assertEquals("UNAUTHORIZED", result.getError().getCode(), 
                    "Error code should be UNAUTHORIZED");

                // getError().getMessage() should be "Invalid username or password"
                assertEquals("Invalid username or password", result.getError().getMessage(), 
                    "Error message should be 'Invalid username or password'");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test login with correct username and password: should return token, then verify token using TokenAuthenticationService")
    void testLoginWithCorrectCredentialsAndVerifyToken() {
        // Create a user first
        String username = "logintestuser";
        String password = "correctPassword123";

        // Create user
        var createResultMono = userService.createUser(username, password);
        StepVerifier.create(createResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals(HttpStatus.OK, result.getStatus());
            })
            .verifyComplete();

        // Login with correct credentials
        var loginResultMono = userService.login(username, password);

        String[] tokenHolder = new String[1];
        Long[] userIdHolder = new Long[1];

        StepVerifier.create(loginResultMono)
            .assertNext(result -> {
                // getResponse() should not be null
                assertNotNull(result.getResponse(), "Response should not be null for correct credentials");
                assertNotNull(result.getResponse().getToken(), "Token should not be null");

                // getStatus() should be 200
                assertEquals(HttpStatus.OK, result.getStatus(), 
                    "Status should be OK (200) for correct credentials");

                // getError() should be null
                assertNull(result.getError(), "Error should be null for correct credentials");

                tokenHolder[0] = result.getResponse().getToken();
            })
            .verifyComplete();

        String token = tokenHolder[0];
        assertNotNull(token, "Token should not be null");

        // Get user ID from database for verification
        User user = userRepository.findByUsername(username).orElse(null);
        assertNotNull(user, "User should exist");
        userIdHolder[0] = user.getId();

        // Verify token using TokenAuthenticationService
        var verifyTokenMono = tokenAuthenticationService.verifyToken(token);

        StepVerifier.create(verifyTokenMono)
            .assertNext(authentication -> {
                // Authentication should not be null
                assertNotNull(authentication, "Authentication should not be null for valid token");

                // Authentication should be instance of CustomAuthentication
                assertTrue(authentication instanceof CustomAuthentication, 
                    "Authentication should be instance of CustomAuthentication");

                CustomAuthentication customAuth = (CustomAuthentication) authentication;

                // Verify getName() returns the user ID as string
                assertEquals(String.valueOf(userIdHolder[0]), customAuth.getName(), 
                    "getName() should return the user ID as string");

                // Verify getUserId() returns the user ID as Long
                assertEquals(userIdHolder[0], customAuth.getUserId(), 
                    "getUserId() should return the user ID as Long");

                // Verify getCredentials() returns the random string part of the token
                assertNotNull(customAuth.getCredentials(), 
                    "getCredentials() should not be null");
                // The credentials should be the second part of the token (after the dot)
                String[] tokenParts = token.split("\\.");
                assertEquals(tokenParts[1], customAuth.getCredentials(), 
                    "getCredentials() should return the random string part of the token");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test logout with me=true: should remove current token from Redis and return success")
    void testLogoutWithMeTrue() {
        // Create a user and login to get a token
        String username = "logoutmeuser";
        String password = "password123";

        // Create user
        var createResultMono = userService.createUser(username, password);
        StepVerifier.create(createResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals(HttpStatus.OK, result.getStatus());
            })
            .verifyComplete();

        // Login to get token
        var loginResultMono = userService.login(username, password);
        String[] tokenHolder = new String[1];
        Long[] userIdHolder = new Long[1];
        String[] randomTokenHolder = new String[1];

        StepVerifier.create(loginResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertNotNull(result.getResponse().getToken());
                tokenHolder[0] = result.getResponse().getToken();
            })
            .verifyComplete();

        // Extract userId and randomToken from token
        User user = userRepository.findByUsername(username).orElse(null);
        assertNotNull(user);
        userIdHolder[0] = user.getId();
        String[] tokenParts = tokenHolder[0].split("\\.");
        randomTokenHolder[0] = tokenParts[1];

        // Verify token exists in Redis before logout
        String redisKey = "token:" + userIdHolder[0];
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        StepVerifier.create(hashOps.get(redisKey, randomTokenHolder[0]))
            .assertNext(value -> {
                assertEquals("true", value, "Token should exist in Redis before logout");
            })
            .verifyComplete();

        // Logout with me=true
        var logoutResultMono = userService.logout(userIdHolder[0], randomTokenHolder[0], true, false);

        StepVerifier.create(logoutResultMono)
            .assertNext(result -> {
                // getResponse() should not be null
                assertNotNull(result.getResponse(), "Response should not be null for successful logout");
                assertEquals("LOGGED_OUT", result.getResponse().getCode(), 
                    "Response code should be LOGGED_OUT");
                assertEquals("logged out successfully", result.getResponse().getMessage(), 
                    "Response message should be 'logged out successfully'");

                // getStatus() should be 200
                assertEquals(HttpStatus.OK, result.getStatus(), 
                    "Status should be OK (200) for successful logout");

                // getError() should be null
                assertNull(result.getError(), "Error should be null for successful logout");
            })
            .verifyComplete();

        // Verify token is removed from Redis
        StepVerifier.create(hashOps.get(redisKey, randomTokenHolder[0]))
            .verifyComplete(); // Should be empty (token removed)
    }

    @Test
    @DisplayName("Test logout with all=true: should remove entire hashset from Redis and return success")
    void testLogoutWithAllTrue() {
        // Create a user and login to get tokens
        String username = "logoutalluser";
        String password = "password123";

        // Create user
        var createResultMono = userService.createUser(username, password);
        StepVerifier.create(createResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals(HttpStatus.OK, result.getStatus());
            })
            .verifyComplete();

        // Login twice to create multiple tokens
        var loginResult1Mono = userService.login(username, password);
        String[] token1Holder = new String[1];
        StepVerifier.create(loginResult1Mono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                token1Holder[0] = result.getResponse().getToken();
            })
            .verifyComplete();

        var loginResult2Mono = userService.login(username, password);
        String[] token2Holder = new String[1];
        StepVerifier.create(loginResult2Mono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                token2Holder[0] = result.getResponse().getToken();
            })
            .verifyComplete();

        // Get userId
        User user = userRepository.findByUsername(username).orElse(null);
        assertNotNull(user);
        Long userId = user.getId();

        // Verify both tokens exist in Redis before logout
        String redisKey = "token:" + userId;
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        String[] token1Parts = token1Holder[0].split("\\.");
        String[] token2Parts = token2Holder[0].split("\\.");
        String randomToken1 = token1Parts[1];
        String randomToken2 = token2Parts[1];

        StepVerifier.create(hashOps.get(redisKey, randomToken1))
            .assertNext(value -> assertEquals("true", value))
            .verifyComplete();
        StepVerifier.create(hashOps.get(redisKey, randomToken2))
            .assertNext(value -> assertEquals("true", value))
            .verifyComplete();

        // Logout with all=true (using any randomToken, it doesn't matter for all=true)
        var logoutResultMono = userService.logout(userId, randomToken1, false, true);

        StepVerifier.create(logoutResultMono)
            .assertNext(result -> {
                // getResponse() should not be null
                assertNotNull(result.getResponse(), "Response should not be null for successful logout");
                assertEquals("LOGGED_OUT", result.getResponse().getCode(), 
                    "Response code should be LOGGED_OUT");
                assertEquals("logged out successfully", result.getResponse().getMessage(), 
                    "Response message should be 'logged out successfully'");

                // getStatus() should be 200
                assertEquals(HttpStatus.OK, result.getStatus(), 
                    "Status should be OK (200) for successful logout");

                // getError() should be null
                assertNull(result.getError(), "Error should be null for successful logout");
            })
            .verifyComplete();

        // Verify entire hashset is removed from Redis
        StepVerifier.create(hashOps.get(redisKey, randomToken1))
            .verifyComplete(); // Should be empty (hashset deleted)
        StepVerifier.create(hashOps.get(redisKey, randomToken2))
            .verifyComplete(); // Should be empty (hashset deleted)
    }

    @Test
    @DisplayName("Test logout with both me=true and all=true: should remove entire hashset (all takes precedence)")
    void testLogoutWithBothMeAndAllTrue() {
        // Create a user and login
        String username = "logoutbothuser";
        String password = "password123";

        var createResultMono = userService.createUser(username, password);
        StepVerifier.create(createResultMono)
            .assertNext(result -> assertEquals(HttpStatus.OK, result.getStatus()))
            .verifyComplete();

        var loginResultMono = userService.login(username, password);
        String[] tokenHolder = new String[1];
        StepVerifier.create(loginResultMono)
            .assertNext(result -> tokenHolder[0] = result.getResponse().getToken())
            .verifyComplete();

        User user = userRepository.findByUsername(username).orElse(null);
        assertNotNull(user);
        Long userId = user.getId();
        String[] tokenParts = tokenHolder[0].split("\\.");
        String randomToken = tokenParts[1];

        // Logout with both me=true and all=true
        var logoutResultMono = userService.logout(userId, randomToken, true, true);

        StepVerifier.create(logoutResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals("LOGGED_OUT", result.getResponse().getCode());
                assertEquals(HttpStatus.OK, result.getStatus());
                assertNull(result.getError());
            })
            .verifyComplete();

        // Verify entire hashset is removed (all takes precedence)
        String redisKey = "token:" + userId;
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        StepVerifier.create(hashOps.get(redisKey, randomToken))
            .verifyComplete(); // Should be empty (hashset deleted)
    }

    @Test
    @DisplayName("Test logout with neither me nor all true: should return error with status 400, code BAD_REQUEST")
    void testLogoutWithNeitherMeNorAllTrue() {
        // Create a user and login
        String username = "logoutfailuser";
        String password = "password123";

        var createResultMono = userService.createUser(username, password);
        StepVerifier.create(createResultMono)
            .assertNext(result -> assertEquals(HttpStatus.OK, result.getStatus()))
            .verifyComplete();

        var loginResultMono = userService.login(username, password);
        String[] tokenHolder = new String[1];
        StepVerifier.create(loginResultMono)
            .assertNext(result -> tokenHolder[0] = result.getResponse().getToken())
            .verifyComplete();

        User user = userRepository.findByUsername(username).orElse(null);
        assertNotNull(user);
        Long userId = user.getId();
        String[] tokenParts = tokenHolder[0].split("\\.");
        String randomToken = tokenParts[1];

        // Logout with both me=false and all=false
        var logoutResultMono = userService.logout(userId, randomToken, false, false);

        StepVerifier.create(logoutResultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for invalid logout request");

                // getStatus() should be 400
                assertEquals(HttpStatus.BAD_REQUEST, result.getStatus(), 
                    "Status should be BAD_REQUEST (400) for invalid logout request");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for invalid logout request");

                // getError().getCode() should be BAD_REQUEST
                assertEquals("BAD_REQUEST", result.getError().getCode(), 
                    "Error code should be BAD_REQUEST");

                // getError().getMessage() should be "Either 'me' or 'all' must be true"
                assertEquals("Either 'me' or 'all' must be true", result.getError().getMessage(), 
                    "Error message should be 'Either 'me' or 'all' must be true'");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test changePassword with correct old password: should update password successfully")
    void testChangePasswordWithCorrectOldPassword() {
        // Create a user
        String username = "changepassuser";
        String oldPassword = "oldPassword123";
        String newPassword = "newPassword456";

        var createResultMono = userService.createUser(username, oldPassword);
        Long[] userIdHolder = new Long[1];
        StepVerifier.create(createResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals(HttpStatus.OK, result.getStatus());
            })
            .verifyComplete();

        User user = userRepository.findByUsername(username).orElse(null);
        assertNotNull(user);
        userIdHolder[0] = user.getId();

        // Change password with correct old password
        var changePasswordResultMono = userService.changePassword(userIdHolder[0], oldPassword, newPassword);

        StepVerifier.create(changePasswordResultMono)
            .assertNext(result -> {
                // getResponse() should not be null
                assertNotNull(result.getResponse(), "Response should not be null for successful password change");
                assertEquals("PASSWORD_UPDATED", result.getResponse().getCode(), 
                    "Response code should be PASSWORD_UPDATED");
                assertEquals("password updated successfully", result.getResponse().getMessage(), 
                    "Response message should be 'password updated successfully'");

                // getStatus() should be 200
                assertEquals(HttpStatus.OK, result.getStatus(), 
                    "Status should be OK (200) for successful password change");

                // getError() should be null
                assertNull(result.getError(), "Error should be null for successful password change");
            })
            .verifyComplete();

        // Verify password was actually changed - try to login with new password
        var loginResultMono = userService.login(username, newPassword);
        StepVerifier.create(loginResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals(HttpStatus.OK, result.getStatus());
            })
            .verifyComplete();

        // Verify old password no longer works
        var loginWithOldPasswordMono = userService.login(username, oldPassword);
        StepVerifier.create(loginWithOldPasswordMono)
            .assertNext(result -> {
                assertEquals(HttpStatus.UNAUTHORIZED, result.getStatus());
                assertNotNull(result.getError());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test changePassword with wrong old password: should return error with status 401, code UNAUTHORIZED")
    void testChangePasswordWithWrongOldPassword() {
        // Create a user
        String username = "changepasswronguser";
        String correctPassword = "correctPassword123";
        String wrongOldPassword = "wrongOldPassword123";
        String newPassword = "newPassword456";

        var createResultMono = userService.createUser(username, correctPassword);
        Long[] userIdHolder = new Long[1];
        StepVerifier.create(createResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals(HttpStatus.OK, result.getStatus());
            })
            .verifyComplete();

        User user = userRepository.findByUsername(username).orElse(null);
        assertNotNull(user);
        userIdHolder[0] = user.getId();

        // Change password with wrong old password
        var changePasswordResultMono = userService.changePassword(userIdHolder[0], wrongOldPassword, newPassword);

        StepVerifier.create(changePasswordResultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for wrong old password");

                // getStatus() should be 401
                assertEquals(HttpStatus.UNAUTHORIZED, result.getStatus(), 
                    "Status should be UNAUTHORIZED (401) for wrong old password");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for wrong old password");

                // getError().getCode() should be UNAUTHORIZED
                assertEquals("UNAUTHORIZED", result.getError().getCode(), 
                    "Error code should be UNAUTHORIZED");

                // getError().getMessage() should be "Invalid old password"
                assertEquals("Invalid old password", result.getError().getMessage(), 
                    "Error message should be 'Invalid old password'");
            })
            .verifyComplete();

        // Verify password was NOT changed - old password should still work
        var loginResultMono = userService.login(username, correctPassword);
        StepVerifier.create(loginResultMono)
            .assertNext(result -> {
                assertNotNull(result.getResponse());
                assertEquals(HttpStatus.OK, result.getStatus());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Test changePassword with non-existent user: should return error with status 404, code NOT_FOUND")
    void testChangePasswordWithNonExistentUser() {
        Long nonExistentUserId = 99999L;
        String oldPassword = "oldPassword123";
        String newPassword = "newPassword456";

        // Try to change password for non-existent user
        var changePasswordResultMono = userService.changePassword(nonExistentUserId, oldPassword, newPassword);

        StepVerifier.create(changePasswordResultMono)
            .assertNext(result -> {
                // getResponse() should be null
                assertNull(result.getResponse(), "Response should be null for non-existent user");

                // getStatus() should be 404
                assertEquals(HttpStatus.NOT_FOUND, result.getStatus(), 
                    "Status should be NOT_FOUND (404) for non-existent user");

                // getError() should not be null
                assertNotNull(result.getError(), "Error should not be null for non-existent user");

                // getError().getCode() should be NOT_FOUND
                assertEquals("NOT_FOUND", result.getError().getCode(), 
                    "Error code should be NOT_FOUND");

                // getError().getMessage() should be "User not found"
                assertEquals("User not found", result.getError().getMessage(), 
                    "Error message should be 'User not found'");
            })
            .verifyComplete();
    }
}

