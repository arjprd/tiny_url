package com.example.tinyurl.service;

import com.example.tinyurl.entity.User;
import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.model.LoginResponse;
import com.example.tinyurl.model.SuccessResponse;
import com.example.tinyurl.repository.UserRepository;
import com.example.tinyurl.util.AESUtil;
import com.example.tinyurl.util.CryptoUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${auth.aes.secret.key}")
    private String aesSecretKey;

    @Value("${auth.token.random.length:32}")
    private int tokenRandomLength;

    @Value("${auth.token.ttl:3600}")
    private long tokenTtlSeconds;

    public UserService(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Validates if the username is alphanumeric
     */
    public boolean isAlphanumeric(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return username.matches("^[a-zA-Z0-9]+$");
    }

    /**
     * Creates a new user
     */
    public Mono<CreateUserResult> createUser(String username, String password) {
        // Validate username is alphanumeric
        if (!isAlphanumeric(username)) {
            ErrorResponse error = new ErrorResponse("INVALID_USERNAME", "Username must be alphanumeric");
            return Mono.just(new CreateUserResult(null, error, HttpStatus.BAD_REQUEST));
        }

        // Check if username already exists
        return Mono.fromCallable(() -> userRepository.existsByUsername(username))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(exists -> {
                if (exists) {
                    ErrorResponse error = new ErrorResponse("DUPLICATE_REQUEST", "Username already exists");
                    return Mono.just(new CreateUserResult(null, error, HttpStatus.CONFLICT));
                }

                // Hash password with bcrypt
                String passwordHash = passwordEncoder.encode(password);

                // Create and save user
                User newUser = new User(username, passwordHash);
                return Mono.fromCallable(() -> userRepository.save(newUser))
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(saved -> {
                        SuccessResponse response = new SuccessResponse("SUCCESS", "User created successfully.");
                        return new CreateUserResult(response, null, HttpStatus.OK);
                    });
            })
            .onErrorResume(e -> {
                ErrorResponse error = new ErrorResponse("SERVER_ERROR", "Something went wrong");
                return Mono.just(new CreateUserResult(null, error, HttpStatus.INTERNAL_SERVER_ERROR));
            });
    }

    /**
     * Logs in a user and generates a token
     */
    public Mono<LoginResult> login(String username, String password) {
        // Validate username is alphanumeric
        if (!isAlphanumeric(username)) {
            ErrorResponse error = new ErrorResponse("INVALID_USERNAME", "Username must be alphanumeric");
            return Mono.just(new LoginResult(null, error, HttpStatus.BAD_REQUEST));
        }

        // Get user by username
        return Mono.fromCallable(() -> userRepository.findByUsername(username))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(optional -> {
                if (optional.isEmpty()) {
                    ErrorResponse error = new ErrorResponse("UNAUTHORIZED", "Invalid username or password");
                    return Mono.just(new LoginResult(null, error, HttpStatus.UNAUTHORIZED));
                }

                User user = optional.get();
                
                // Verify password using bcrypt
                if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                    ErrorResponse error = new ErrorResponse("UNAUTHORIZED", "Invalid username or password");
                    return Mono.just(new LoginResult(null, error, HttpStatus.UNAUTHORIZED));
                }

                // Encrypt user.id using AES-256
                String encryptedUserId = AESUtil.encrypt(String.valueOf(user.getId()), aesSecretKey);

                // Generate random crypto string
                String randomString = CryptoUtil.generateRandomString(tokenRandomLength);

                // Store in Redis as hash set: key = token:<user.id>, field = <random_string>, value = "true"
                String redisKey = "token:" + user.getId();
                ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
                
                return hashOps.put(redisKey, randomString, "true")
                    .then(hashOps.expire(redisKey, Duration.ofSeconds(tokenTtlSeconds), Arrays.asList(randomString)))
                    .then(Mono.fromCallable(() -> {
                        // Construct token: <encrypted(user.id)>.<random_string>
                        String token = encryptedUserId + "." + randomString;
                        return new LoginResult(new LoginResponse(token), null, HttpStatus.OK);
                    }))
                    .subscribeOn(Schedulers.boundedElastic());
            })
            .onErrorResume(e -> {
                ErrorResponse error = new ErrorResponse("SERVER_ERROR", "Something went wrong");
                return Mono.just(new LoginResult(null, error, HttpStatus.INTERNAL_SERVER_ERROR));
            });
    }

    /**
     * Logs out a user by removing token(s) from Redis
     * Logic:
     * 1. If body.me is true, remove the current user's random key field from redis hashset token:<userId>
     * 2. If body.all is true, remove the whole hashset token:<userId>
     */
    public Mono<LogoutResult> logout(Long userId, String randomToken, Boolean me, Boolean all) {
        String redisKey = "token:" + userId;
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        Mono<Void> logoutOperation;

        if (Boolean.TRUE.equals(all)) {
            // Remove the whole hashset
            logoutOperation = redisTemplate.delete(redisKey).then();
        } else if (Boolean.TRUE.equals(me) && randomToken != null) {
            // Remove the specific field from hashset
            logoutOperation = hashOps.remove(redisKey, randomToken).then();
        } else {
            // No valid operation specified
            ErrorResponse error = new ErrorResponse("BAD_REQUEST", "Either 'me' or 'all' must be true");
            return Mono.just(new LogoutResult(null, error, HttpStatus.BAD_REQUEST));
        }

        return logoutOperation
            .then(Mono.fromCallable(() -> {
                SuccessResponse response = new SuccessResponse("LOGGED_OUT", "logged out successfully");
                return new LogoutResult(response, null, HttpStatus.OK);
            }))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(e -> {
                ErrorResponse error = new ErrorResponse("SERVER_ERROR", "Something went wrong");
                return Mono.just(new LogoutResult(null, error, HttpStatus.INTERNAL_SERVER_ERROR));
            });
    }

    /**
     * Changes user password
     * Logic:
     * 1. Get user password hash from table
     * 2. Compare the hash of body.old_password and the db one
     * 3. If both are same, hash the new password in body.new_password and update the user's password_hash
     */
    public Mono<ChangePasswordResult> changePassword(Long userId, String oldPassword, String newPassword) {
        // Get user by userId
        return Mono.fromCallable(() -> userRepository.findById(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(optional -> {
                if (optional.isEmpty()) {
                    ErrorResponse error = new ErrorResponse("NOT_FOUND", "User not found");
                    return Mono.just(new ChangePasswordResult(null, error, HttpStatus.NOT_FOUND));
                }

                User user = optional.get();

                // Compare the hash of old_password and the db one
                if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
                    ErrorResponse error = new ErrorResponse("UNAUTHORIZED", "Invalid old password");
                    return Mono.just(new ChangePasswordResult(null, error, HttpStatus.UNAUTHORIZED));
                }

                // Hash the new password and update the user's password_hash
                String newPasswordHash = passwordEncoder.encode(newPassword);
                user.setPasswordHash(newPasswordHash);

                return Mono.fromCallable(() -> userRepository.save(user))
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(saved -> {
                        SuccessResponse response = new SuccessResponse("PASSWORD_UPDATED", "password updated successfully");
                        return new ChangePasswordResult(response, null, HttpStatus.OK);
                    });
            })
            .onErrorResume(e -> {
                ErrorResponse error = new ErrorResponse("SERVER_ERROR", "Something went wrong");
                return Mono.just(new ChangePasswordResult(null, error, HttpStatus.INTERNAL_SERVER_ERROR));
            });
    }

    // Inner class for result handling
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CreateUserResult {
        private final SuccessResponse response;
        private final ErrorResponse error;
        private final HttpStatus status;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class LoginResult {
        private final LoginResponse response;
        private final ErrorResponse error;
        private final HttpStatus status;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class LogoutResult {
        private final SuccessResponse response;
        private final ErrorResponse error;
        private final HttpStatus status;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class ChangePasswordResult {
        private final SuccessResponse response;
        private final ErrorResponse error;
        private final HttpStatus status;
    }
}

