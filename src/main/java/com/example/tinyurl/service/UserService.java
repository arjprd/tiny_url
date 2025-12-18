package com.example.tinyurl.service;

import com.example.tinyurl.entity.User;
import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.model.SuccessResponse;
import com.example.tinyurl.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

    // Inner class for result handling
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CreateUserResult {
        private final SuccessResponse response;
        private final ErrorResponse error;
        private final HttpStatus status;
    }
}

