package com.example.tinyurl.controller;

import com.example.tinyurl.model.CreateUserRequest;
import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.model.SuccessResponse;
import com.example.tinyurl.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

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
}

