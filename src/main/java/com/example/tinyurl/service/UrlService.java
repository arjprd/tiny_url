package com.example.tinyurl.service;

import com.example.tinyurl.entity.ShortUrl;
import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.model.ShortenResponse;
import com.example.tinyurl.repository.ShortUrlRepository;
import com.example.tinyurl.util.Base62Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Service
public class UrlService {

    private final ShortUrlRepository shortUrlRepository;
    
    @Value("${app.host:http://localhost:8080}")
    private String host;

    public UrlService(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    /**
     * Validates if the provided string is a valid URL
     */
    public boolean isValidUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }
        try {
            new URL(urlString);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Creates SHA256 hash of the URL
     */
    public String createHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Shortens a URL
     */
    public Mono<ShortenResult> shortenUrl(String longUrl) {
        // Validate URL
        if (!isValidUrl(longUrl)) {
            ErrorResponse error = new ErrorResponse("INVALID_URL", "Provided URL is invalid");
            return Mono.just(new ShortenResult(null, error, HttpStatus.UNPROCESSABLE_ENTITY));
        }

        // Create hash
        String longUrlHash = createHash(longUrl);

        // Check if URL already exists
        return Mono.fromCallable(() -> 
            shortUrlRepository.findByLongUrlHashAndLongUrl(longUrlHash, longUrl)
        )
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(optional -> {
            if (optional.isPresent()) {
                // URL already exists - return error
                ErrorResponse error = new ErrorResponse("DUPLICATE_REQUEST", "A short URL exists for the long URL");
                return Mono.just(new ShortenResult(null, error, HttpStatus.CONFLICT));
            }

            // Insert new record
            ShortUrl newShortUrl = new ShortUrl(longUrl, longUrlHash);
            return Mono.fromCallable(() -> shortUrlRepository.save(newShortUrl))
                .subscribeOn(Schedulers.boundedElastic())
                .map(saved -> {
                    String shortUrl = host + "/" + Base62Util.encode(saved.getId());
                    return new ShortenResult(new ShortenResponse(shortUrl), null, HttpStatus.OK);
                });
        })
        .onErrorResume(e -> {
            ErrorResponse error = new ErrorResponse("SERVER_ERROR", "Something went wrong");
            return Mono.just(new ShortenResult(null, error, HttpStatus.INTERNAL_SERVER_ERROR));
        });
    }

    /**
     * Retrieves the long URL from a short URL
     */
    public Mono<RedirectResult> getLongUrl(String shortUrlEncoded) {
        try {
            long id = Base62Util.decode(shortUrlEncoded);
            
            return Mono.fromCallable(() -> shortUrlRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .map(optional -> {
                    if (optional.isPresent()) {
                        return new RedirectResult(optional.get().getLongUrl(), null, HttpStatus.MOVED_PERMANENTLY);
                    } else {
                        ErrorResponse error = new ErrorResponse("NO_RECORD", "A long URL does exists for the short URL");
                        return new RedirectResult(null, error, HttpStatus.NOT_FOUND);
                    }
                })
                .onErrorResume(e -> {
                    ErrorResponse error = new ErrorResponse("NO_RECORD", "A long URL does exists for the short URL");
                    return Mono.just(new RedirectResult(null, error, HttpStatus.NOT_FOUND));
                });
        } catch (IllegalArgumentException e) {
            ErrorResponse error = new ErrorResponse("NO_RECORD", "A long URL does exists for the short URL");
            return Mono.just(new RedirectResult(null, error, HttpStatus.NOT_FOUND));
        }
    }

    // Inner classes for result handling
    @Getter
    @AllArgsConstructor
    public static class ShortenResult {
        private final ShortenResponse response;
        private final ErrorResponse error;
        private final HttpStatus status;
    }

    @Getter
    @AllArgsConstructor
    public static class RedirectResult {
        private final String longUrl;
        private final ErrorResponse error;
        private final HttpStatus status;
    }
}

