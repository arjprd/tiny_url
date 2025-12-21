package com.example.tinyurl.service;

import com.example.tinyurl.entity.CustomUrlCode;
import com.example.tinyurl.entity.ShortUrlClickAnalytics;
import com.example.tinyurl.entity.ShortUrlClickAnalyticsId;
import com.example.tinyurl.repository.CustomUrlCodeRepository;
import com.example.tinyurl.repository.ShortUrlClickAnalyticsRepository;
import com.example.tinyurl.util.Base62Util;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate;
    private final CustomUrlCodeRepository customUrlCodeRepository;
    private final ShortUrlClickAnalyticsRepository analyticsRepository;

    @Value("${analytics.time.key.format:year.month.day.hour}")
    private String timeKeyFormat;

    /**
     * Captures a click event for analytics
     * Updates a Redis hash with key as `analytics:t_key`, field as `shortUrl`, and increments the value
     * This method is async and fire-and-forget - it doesn't block the calling thread
     * 
     * @param shortUrl The short URL code (e.g., "_abc123" or "customCode")
     * @param timestamp The timestamp of the click event
     */
    public void click(String shortUrl, OffsetDateTime timestamp) {
        String tKey = generateTimeKey(timestamp);
        String redisKey = "analytics:" + tKey;
        
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        
        // Increment the count for this shortUrl in the hash
        // HINCRBY analytics:t_key shortUrl 1
        // Fire and forget - subscribe on a separate scheduler and don't block
        hashOps.increment(redisKey, shortUrl, 1L)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                count -> {
                    // Successfully incremented - analytics captured
                },
                error -> {
                    // Log error but don't fail the request
                    // In production, you might want to log this to a monitoring system
                }
            );
    }

    /**
     * Generates a time-based key (t_key) based on the configured format and timestamp
     * 
     * Format can be a combination of: year, month, day, hour, minute, seconds
     * Must be in order: year.month.day.hour.minute.seconds
     * Examples:
     * - "year.month" -> "2025.12"
     * - "year.month.day" -> "2025.12.21"
     * - "year.month.day.hour" -> "2025.12.21.10"
     * 
     * @param timestamp The timestamp to generate the key from
     * @return The generated time key string
     */
    public String generateTimeKey(OffsetDateTime timestamp) {
        List<String> formatParts = Arrays.asList(timeKeyFormat.split("\\."));
        
        StringBuilder keyBuilder = new StringBuilder();
        
        for (int i = 0; i < formatParts.size(); i++) {
            if (i > 0) {
                keyBuilder.append(".");
            }
            
            String part = formatParts.get(i).trim();
            switch (part) {
                case "year":
                    keyBuilder.append(timestamp.getYear());
                    break;
                case "month":
                    keyBuilder.append(String.format("%02d", timestamp.getMonthValue()));
                    break;
                case "day":
                    keyBuilder.append(String.format("%02d", timestamp.getDayOfMonth()));
                    break;
                case "hour":
                    keyBuilder.append(String.format("%02d", timestamp.getHour()));
                    break;
                case "minute":
                    keyBuilder.append(String.format("%02d", timestamp.getMinute()));
                    break;
                case "seconds":
                    keyBuilder.append(String.format("%02d", timestamp.getSecond()));
                    break;
                default:
                    // Unknown format part - skip it or throw exception
                    throw new IllegalArgumentException("Unknown time key format part: " + part);
            }
        }
        
        return keyBuilder.toString();
    }

    /**
     * Parses a time key (t_key) back to a timestamp with rest as 0
     * Example: if config is "year.month.day.hour" and t_key is "2025.12.21.10",
     * then returns "2025-12-21T10:00:00Z"
     * 
     * @param tKey The time key string (e.g., "2025.12.21.10")
     * @return OffsetDateTime with the parsed values and rest set to 0
     */
    public OffsetDateTime parseTimeKey(String tKey) {
        List<String> formatParts = Arrays.asList(timeKeyFormat.split("\\."));
        String[] keyParts = tKey.split("\\.");
        
        if (keyParts.length != formatParts.size()) {
            throw new IllegalArgumentException("Time key format mismatch. Expected " + formatParts.size() + " parts, got " + keyParts.length);
        }
        
        int year = OffsetDateTime.now().getYear();
        int month = 1;
        int day = 1;
        int hour = 0;
        int minute = 0;
        int second = 0;
        
        for (int i = 0; i < formatParts.size(); i++) {
            String part = formatParts.get(i).trim();
            int value = Integer.parseInt(keyParts[i]);
            
            switch (part) {
                case "year":
                    year = value;
                    break;
                case "month":
                    month = value;
                    break;
                case "day":
                    day = value;
                    break;
                case "hour":
                    hour = value;
                    break;
                case "minute":
                    minute = value;
                    break;
                case "seconds":
                    second = value;
                    break;
            }
        }
        
        return OffsetDateTime.of(year, month, day, hour, minute, second, 0, java.time.ZoneOffset.UTC);
    }

    /**
     * Resolves url_id from shortUrl code
     * If shortUrl has '_' prefix, base62 decode it
     * Otherwise, look up in custom_url_code table
     * 
     * @param shortUrl The short URL code
     * @return Mono containing the url_id, or empty if not found
     */
    public Mono<Long> resolveUrlId(String shortUrl) {
        if (shortUrl.startsWith("_")) {
            // Base62 encoded URL
            try {
                String encoded = shortUrl.substring(1); // Remove '_' prefix
                long id = Base62Util.decode(encoded);
                return Mono.just(id);
            } catch (IllegalArgumentException e) {
                return Mono.empty();
            }
        } else {
            // Custom URL code - look up in database
            return Mono.fromCallable(() -> customUrlCodeRepository.findByCode(shortUrl))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        CustomUrlCode customCode = optional.get();
                        return Mono.just(customCode.getUrl().getId());
                    } else {
                        return Mono.empty();
                    }
                });
        }
    }

    /**
     * Dumps analytics data from Redis to database for a specific time key
     * After successful dump, deletes the Redis key
     * 
     * @param tKey The time key to dump (e.g., "2025.12.21.10")
     * @return Mono that completes when dump is finished and key is deleted
     */
    public Mono<Void> dumpAnalyticsForTimeKey(String tKey) {
        String redisKey = "analytics:" + tKey;
        ReactiveHashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        
        // Get all entries from the hash
        return hashOps.entries(redisKey)
            .collectMap(Map.Entry::getKey, entry -> {
                try {
                    return Long.parseLong(entry.getValue());
                } catch (NumberFormatException e) {
                    return 0L;
                }
            })
            .flatMapMany(shortUrlCounts -> {
                // Process each shortUrl
                return Flux.fromIterable(shortUrlCounts.entrySet())
                    .flatMap(entry -> {
                        String shortUrl = entry.getKey();
                        Long count = entry.getValue();
                        
                        // Resolve url_id
                        return resolveUrlId(shortUrl)
                            .flatMap(urlId -> {
                                // Parse timestamp from t_key
                                OffsetDateTime timestamp = parseTimeKey(tKey);
                                
                                // Save or update in database
                                return Mono.fromCallable(() -> {
                                    ShortUrlClickAnalyticsId id = new ShortUrlClickAnalyticsId(timestamp, urlId);
                                    return analyticsRepository.findById(id);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(optional -> {
                                    if (optional.isPresent()) {
                                        // Update existing record
                                        ShortUrlClickAnalytics existing = optional.get();
                                        existing.setCount(existing.getCount() + count);
                                        return Mono.fromCallable(() -> analyticsRepository.save(existing))
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .then();
                                    } else {
                                        // Create new record
                                        ShortUrlClickAnalytics analytics = new ShortUrlClickAnalytics(timestamp, urlId, count);
                                        return Mono.fromCallable(() -> analyticsRepository.save(analytics))
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .then();
                                    }
                                });
                            })
                            .onErrorResume(e -> {
                                // Log error but continue processing other entries
                                return Mono.empty();
                            });
                    });
            })
            .then()
            .then(redisTemplate.delete(redisKey))
            .then()
            .onErrorResume(e -> {
                // Log error but don't fail the entire dump
                // Note: Key deletion happens even if some entries failed
                return Mono.empty();
            });
    }
}

