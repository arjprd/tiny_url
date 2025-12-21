package com.example.tinyurl.service;

import com.example.tinyurl.entity.CustomUrlCode;
import com.example.tinyurl.entity.ShortUrl;
import com.example.tinyurl.entity.User;
import com.example.tinyurl.model.ErrorResponse;
import com.example.tinyurl.model.ShortenResponse;
import com.example.tinyurl.repository.CustomUrlCodeRepository;
import com.example.tinyurl.repository.ShortUrlRepository;
import com.example.tinyurl.util.Base62Util;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final CustomUrlCodeRepository customUrlCodeRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final EntityManager entityManager;
    
    @Value("${app.host:http://localhost:8080}")
    private String host;

    @Value("${cache.short.url.ttl:3600}")
    private long cacheTtlSeconds;

    @Value("${cache.lock.ttl:10}")
    private long lockTtlSeconds;

    public UrlService(ShortUrlRepository shortUrlRepository,
                     CustomUrlCodeRepository customUrlCodeRepository,
                     @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                     EntityManager entityManager) {
        this.shortUrlRepository = shortUrlRepository;
        this.customUrlCodeRepository = customUrlCodeRepository;
        this.redisTemplate = redisTemplate;
        this.entityManager = entityManager;
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
     * @param longUrl The URL to shorten
     * @param customShortUrl Optional custom short URL code
     * @param expiry Optional expiry timestamp
     * @param userId The user ID from request context (from token)
     */
    public Mono<ShortenResult> shortenUrl(String longUrl, String customShortUrl, OffsetDateTime expiry, Long userId) {
        // Validate URL
        if (!isValidUrl(longUrl)) {
            ErrorResponse error = new ErrorResponse("INVALID_URL", "Provided URL is invalid");
            return Mono.just(new ShortenResult(null, error, HttpStatus.BAD_REQUEST));
        }

        // If custom short URL is provided, check if it already exists
        if (customShortUrl != null && !customShortUrl.trim().isEmpty()) {
            return Mono.fromCallable(() -> 
                customUrlCodeRepository.findByCode(customShortUrl)
            )
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(optional -> {
                if (optional.isPresent()) {
                    // Custom short URL already exists - return error
                    ErrorResponse error = new ErrorResponse("DUPLICATE_REQUEST", "Custom short URL already exists");
                    return Mono.just(new ShortenResult(null, error, HttpStatus.CONFLICT));
                }

                // Continue with URL creation
                return createShortUrl(longUrl, customShortUrl, expiry, userId);
            });
        } else {
            // No custom short URL - proceed with normal flow
            return createShortUrl(longUrl, null, expiry, userId);
        }
    }

    /**
     * Creates a short URL record
     */
    private Mono<ShortenResult> createShortUrl(String longUrl, String customShortUrl, OffsetDateTime expiry, Long userId) {
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

            // Insert new record with owner
            ShortUrl newShortUrl = new ShortUrl(longUrl, longUrlHash, new User(userId));
            newShortUrl.setExpiry(expiry);
            return Mono.fromCallable(() -> {
                ShortUrl savedShortUrl = shortUrlRepository.save(newShortUrl);
                if (customShortUrl != null && !customShortUrl.trim().isEmpty()) { 
                    CustomUrlCode customCode = new CustomUrlCode(customShortUrl, savedShortUrl);
                    customUrlCodeRepository.save(customCode);
                }
                return savedShortUrl;
            })
                .subscribeOn(Schedulers.boundedElastic())
                .map(saved -> {
                    if (customShortUrl != null && !customShortUrl.trim().isEmpty()) { 
                        String responseShortUrl = host + "/" + customShortUrl;
                        return new ShortenResult(new ShortenResponse(responseShortUrl), null, HttpStatus.OK);
                    } else {
                        String encoded = "_" + Base62Util.encode(saved.getId());
                        String responseShortUrl = host + "/" + encoded;
                        return new ShortenResult(new ShortenResponse(responseShortUrl), null, HttpStatus.OK);
                    }
                });
        })
        .onErrorResume(e -> {
            ErrorResponse error = new ErrorResponse("SERVER_ERROR", "Something went wrong");
            return Mono.just(new ShortenResult(null, error, HttpStatus.INTERNAL_SERVER_ERROR));
        });
    }

    /**
     * Retrieves the long URL from a short URL
     * Implements caching with Redis lock to prevent multiple DB queries for the same record
     * Logic:
     * - If shortURL has prefix '_', proceed with existing flow (Base62 decode)
     * - If no prefix '_', check in custom_url_code table
     * - Check expiry (expiry > current timestamp or is null)
     */
    public Mono<RedirectResult> getLongUrl(String shortUrlCode) {
        String cacheKey = "short:" + shortUrlCode;
        String lockKey = "lock:short:" + shortUrlCode;
        
        ReactiveValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        
        // Step 1: Check Redis cache first
        return valueOps.get(cacheKey)
            .flatMap(cachedLongUrl -> {
                if (cachedLongUrl != null && !cachedLongUrl.isEmpty()) {
                    // Cache hit - return immediately
                    return Mono.just(new RedirectResult(cachedLongUrl, null, HttpStatus.MOVED_PERMANENTLY));
                }
                
                // Cache miss - try to acquire lock
                return acquireLock(lockKey)
                    .flatMap(lockAcquired -> {
                        if (lockAcquired) {
                            // This request acquired the lock - query DB and update cache
                            return queryDbAndUpdateCache(shortUrlCode, cacheKey, lockKey);
                        } else {
                            // Another request has the lock - wait and retry cache
                            return waitAndRetryCache(cacheKey, lockKey, 0);
                        }
                    });
            })
            .switchIfEmpty(
                // Cache key doesn't exist - try to acquire lock
                acquireLock(lockKey)
                    .flatMap(lockAcquired -> {
                        if (lockAcquired) {
                            // This request acquired the lock - query DB and update cache
                            return queryDbAndUpdateCache(shortUrlCode, cacheKey, lockKey);
                        } else {
                            // Another request has the lock - wait and retry cache
                            return waitAndRetryCache(cacheKey, lockKey, 0);
                        }
                    })
            )
            .onErrorResume(e -> {
                ErrorResponse error = new ErrorResponse("NO_RECORD", "A long URL does not exist for the short URL");
                return Mono.just(new RedirectResult(null, error, HttpStatus.NOT_FOUND));
            });
    }

    /**
     * Acquires a distributed lock using Redis SET NX EX
     */
    private Mono<Boolean> acquireLock(String lockKey) {
        String lockValue = UUID.randomUUID().toString();
        ReactiveValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        
        // SET lockKey lockValue NX EX lockTtlSeconds
        return valueOps.setIfAbsent(lockKey, lockValue, Duration.ofSeconds(lockTtlSeconds))
            .map(Boolean::booleanValue);
    }

    /**
     * Queries database and updates Redis cache
     * Handles both Base62 encoded URLs (with '_' prefix) and custom URL codes
     */
    private Mono<RedirectResult> queryDbAndUpdateCache(String shortUrlCode, String cacheKey, String lockKey) {
        ReactiveValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        OffsetDateTime now = OffsetDateTime.now();
        
        // Check if shortURL has prefix '_'
        if (shortUrlCode.startsWith("_")) {
            // Proceed with existing flow - Base62 decode
            try {
                String encoded = shortUrlCode.substring(1); // Remove '_' prefix
                long id = Base62Util.decode(encoded);
                
                return Mono.fromCallable(() -> shortUrlRepository.findById(id))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(optional -> {
                        if (optional.isPresent()) {
                            ShortUrl shortUrl = optional.get();
                            
                            // Check expiry: expiry > current timestamp or is null
                            if (shortUrl.getExpiry() != null && shortUrl.getExpiry().isBefore(now)) {
                                // URL has expired
                                return releaseLock(lockKey)
                                    .then(Mono.just(new RedirectResult(null, 
                                        new ErrorResponse("NO_RECORD", "A long URL does not exist for the short URL"), 
                                        HttpStatus.NOT_FOUND)));
                            }
                            
                            String longUrl = shortUrl.getLongUrl();
                            // Update Redis cache with long URL
                            return valueOps.set(cacheKey, longUrl, Duration.ofSeconds(cacheTtlSeconds))
                                .then(releaseLock(lockKey))
                                .then(Mono.just(new RedirectResult(longUrl, null, HttpStatus.MOVED_PERMANENTLY)));
                        } else {
                            // Record not found - release lock and return error
                            return releaseLock(lockKey)
                                .then(Mono.just(new RedirectResult(null, 
                                    new ErrorResponse("NO_RECORD", "A long URL does not exist for the short URL"), 
                                    HttpStatus.NOT_FOUND)));
                        }
                    })
                    .onErrorResume(e -> {
                        // On error, release lock
                        return releaseLock(lockKey)
                            .then(Mono.just(new RedirectResult(null, 
                                new ErrorResponse("NO_RECORD", "A long URL does not exist for the short URL"), 
                                HttpStatus.NOT_FOUND)));
                    });
            } catch (IllegalArgumentException e) {
                // Invalid Base62 encoding
                return releaseLock(lockKey)
                    .then(Mono.just(new RedirectResult(null, 
                        new ErrorResponse("NO_RECORD", "A long URL does not exist for the short URL"), 
                        HttpStatus.NOT_FOUND)));
            }
        } else {
            // No prefix '_' - check in custom_url_code table
            return Mono.fromCallable(() -> customUrlCodeRepository.findByCode(shortUrlCode))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        CustomUrlCode customCode = optional.get();
                        ShortUrl shortUrl = customCode.getUrl();
                        
                        // Check expiry: expiry > current timestamp or is null
                        if (shortUrl.getExpiry() != null && shortUrl.getExpiry().isBefore(now)) {
                            // URL has expired
                            return releaseLock(lockKey)
                                .then(Mono.just(new RedirectResult(null, 
                                    new ErrorResponse("NO_RECORD", "A long URL does not exist for the short URL"), 
                                    HttpStatus.NOT_FOUND)));
                        }
                        
                        String longUrl = shortUrl.getLongUrl();
                        // Update Redis cache with long URL
                        return valueOps.set(cacheKey, longUrl, Duration.ofSeconds(cacheTtlSeconds))
                            .then(releaseLock(lockKey))
                            .then(Mono.just(new RedirectResult(longUrl, null, HttpStatus.MOVED_PERMANENTLY)));
                    } else {
                        // Custom code not found - release lock and return error
                        return releaseLock(lockKey)
                            .then(Mono.just(new RedirectResult(null, 
                                new ErrorResponse("NO_RECORD", "A long URL does not exist for the short URL"), 
                                HttpStatus.NOT_FOUND)));
                    }
                })
                .onErrorResume(e -> {
                    // On error, release lock
                    return releaseLock(lockKey)
                        .then(Mono.just(new RedirectResult(null, 
                            new ErrorResponse("NO_RECORD", "A long URL does not exist for the short URL"), 
                            HttpStatus.NOT_FOUND)));
                });
        }
    }

    /**
     * Waits for lock to be released and retries cache lookup
     */
    private Mono<RedirectResult> waitAndRetryCache(String cacheKey, String lockKey, int retryCount) {
        final int maxRetries = 20; // Maximum retries (20 * 100ms = 2 seconds max wait)
        final long waitMillis = 100; // Wait 100ms between retries
        
        ReactiveValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        
        if (retryCount >= maxRetries) {
            // Max retries reached - return error
            ErrorResponse error = new ErrorResponse("NO_RECORD", "A long URL does exists for the short URL");
            return Mono.just(new RedirectResult(null, error, HttpStatus.NOT_FOUND));
        }
        
        // Check if lock still exists
        return valueOps.get(lockKey)
            .flatMap(lockValue -> {
                if (lockValue != null) {
                    // Lock still exists - wait and retry
                    return Mono.delay(java.time.Duration.ofMillis(waitMillis))
                        .then(waitAndRetryCache(cacheKey, lockKey, retryCount + 1));
                } else {
                    // Lock released - check cache again
                    return valueOps.get(cacheKey)
                        .flatMap(cachedLongUrl -> {
                            if (cachedLongUrl != null && !cachedLongUrl.isEmpty()) {
                                // Cache now has value - return it
                                return Mono.just(new RedirectResult(cachedLongUrl, null, HttpStatus.MOVED_PERMANENTLY));
                            } else {
                                // Cache still empty - wait a bit more and retry
                                return Mono.delay(java.time.Duration.ofMillis(waitMillis))
                                    .then(waitAndRetryCache(cacheKey, lockKey, retryCount + 1));
                            }
                        })
                        .switchIfEmpty(
                            // Cache key doesn't exist - wait and retry
                            Mono.delay(java.time.Duration.ofMillis(waitMillis))
                                .then(waitAndRetryCache(cacheKey, lockKey, retryCount + 1))
                        );
                }
            })
            .switchIfEmpty(
                // Lock doesn't exist - check cache
                valueOps.get(cacheKey)
                    .flatMap(cachedLongUrl -> {
                        if (cachedLongUrl != null && !cachedLongUrl.isEmpty()) {
                            return Mono.just(new RedirectResult(cachedLongUrl, null, HttpStatus.MOVED_PERMANENTLY));
                        } else {
                            return Mono.delay(java.time.Duration.ofMillis(waitMillis))
                                .then(waitAndRetryCache(cacheKey, lockKey, retryCount + 1));
                        }
                    })
                    .switchIfEmpty(
                        Mono.delay(java.time.Duration.ofMillis(waitMillis))
                            .then(waitAndRetryCache(cacheKey, lockKey, retryCount + 1))
                    )
            );
    }

    /**
     * Releases the distributed lock
     */
    private Mono<Boolean> releaseLock(String lockKey) {
        return redisTemplate.delete(lockKey)
            .map(count -> count > 0);
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

