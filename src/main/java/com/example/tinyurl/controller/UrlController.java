package com.example.tinyurl.controller;

import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.model.ShortenRequest;
import com.example.tinyurl.model.ShortenResponse;
import com.example.tinyurl.service.UrlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/shorten")
    public Mono<ResponseEntity<?>> shortenUrl(@RequestBody ShortenRequest request) {
        return urlService.shortenUrl(request.getUrl())
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

    @GetMapping("/{shortURL}")
    public Mono<ResponseEntity<?>> redirect(@PathVariable String shortURL) {
        return urlService.getLongUrl(shortURL)
            .map(result -> {
                if (result.getError() != null) {
                    // Return error response
                    return ResponseEntity.status(result.getStatus())
                        .body(result.getError());
                } else {
                    // Return 301 redirect
                    return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                        .header("Location", result.getLongUrl())
                        .build();
                }
            });
    }
}

