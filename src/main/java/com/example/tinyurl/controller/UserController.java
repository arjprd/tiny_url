package com.example.tinyurl.controller;

import com.example.tinyurl.model.CreateUserRequest;
import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.model.LoginRequest;
import com.example.tinyurl.model.LoginResponse;
import com.example.tinyurl.model.SuccessResponse;
import com.example.tinyurl.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "User", description = "User management APIs")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Create a new user", description = "Creates a new user with username and password")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User created successfully",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid username (must be alphanumeric)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Username already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/user")
    public Mono<ResponseEntity<?>> createUser(@RequestBody CreateUserRequest request) {
        return userService.createUser(request.getUsername(), request.getPassword())
            .map(result -> {
                if (result.getError() != null) {
                    // Return error response
                    return ResponseEntity.status(result.getStatus())
                        .body(result.getError());
                } else {
                    // Return success response
                    return ResponseEntity.status(result.getStatus())
                        .body(result.getResponse());
                }
            });
    }

    @Operation(summary = "Login user", description = "Authenticates user and returns a token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid username (must be alphanumeric)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid username or password",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/user/login")
    public Mono<ResponseEntity<?>> login(@RequestBody LoginRequest request) {
        return userService.login(request.getUsername(), request.getPassword())
            .map(result -> {
                if (result.getError() != null) {
                    // Return error response
                    return ResponseEntity.status(result.getStatus())
                        .body(result.getError());
                } else {
                    // Return success response
                    return ResponseEntity.status(result.getStatus())
                        .body(result.getResponse());
                }
            });
    }
}

