package com.example.tinyurl.controller;

import com.example.tinyurl.model.AnalyticsResponse;
import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.model.ShortenRequest;
import com.example.tinyurl.model.ShortenResponse;
import com.example.tinyurl.service.UrlService;
import com.example.tinyurl.util.CustomAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@RestController
@Tag(name = "URL", description = "URL shortening and redirection APIs")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @Operation(summary = "Shorten a URL", description = "Creates a short URL for the provided long URL. Requires Bearer Token Authentication.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "URL shortened successfully",
            content = @Content(schema = @Schema(implementation = ShortenResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid URL",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Duplicate request - URL already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/shorten")
    public Mono<ResponseEntity<?>> shortenUrl(@RequestBody ShortenRequest request) {
        // Get userId from request context (set by token authentication)
        return ReactiveSecurityContextHolder.getContext()
            .cast(SecurityContext.class)
            .map(SecurityContext::getAuthentication)
            .cast(CustomAuthentication.class)
            .map(CustomAuthentication::getUserId)
            .flatMap(userId -> urlService.shortenUrl(request.getUrl(), request.getShortUrl(), request.getExpiry(), userId)
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
                }))
            .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "Invalid token"))));
    }

    @Operation(summary = "Redirect to long URL", description = "Redirects to the original long URL using the short URL code")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "301", description = "Redirect to long URL"),
        @ApiResponse(responseCode = "404", description = "Short URL not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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

    @Operation(summary = "Get analytics for a short URL", description = "Retrieves click analytics for a short URL within a time range. Requires Bearer Token Authentication and ownership of the URL.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully",
            content = @Content(schema = @Schema(implementation = AnalyticsResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - User is not the owner of the URL",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Short URL not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/url/{shortUrlCode}")
    public Mono<ResponseEntity<?>> getAnalytics(
            @PathVariable String shortUrlCode,
            @RequestParam("start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {
        // Get userId from request context (set by token authentication)
        return ReactiveSecurityContextHolder.getContext()
            .cast(SecurityContext.class)
            .map(SecurityContext::getAuthentication)
            .cast(CustomAuthentication.class)
            .map(CustomAuthentication::getUserId)
            .flatMap(userId -> urlService.getAnalytics(shortUrlCode, userId, startDate, endDate)
                .map(result -> {
                    if (result.getError() != null) {
                        // Return error response
                        return ResponseEntity.status(result.getStatus())
                            .body(result.getError());
                    } else {
                        // Return success response with analytics list
                        return ResponseEntity.status(result.getStatus())
                            .body(result.getResponse());
                    }
                }))
            .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "Invalid token"))));
    }
}

