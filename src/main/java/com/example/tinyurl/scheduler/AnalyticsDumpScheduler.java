package com.example.tinyurl.scheduler;

import com.example.tinyurl.service.AnalyticsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsDumpScheduler {

    private final AnalyticsService analyticsService;
    private final @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${analytics.time.key.format:year.month.day.hour}")
    private String timeKeyFormat;

    private String dumpCron;
    private long offsetValue;
    private String offsetUnit;

    @PostConstruct
    public void initialize() {
        // Always infer cron and offset from time key format
        String lastPart = getLastPartOfTimeKeyFormat();
        
        // Infer cron schedule based on time key format
        if ("minute".equalsIgnoreCase(lastPart)) {
            dumpCron = "1 * * * * *"; // Every minute at second 1
            offsetValue = 1;
            offsetUnit = "minutes";
            log.info("Inferred from time key format (ends with 'minute'): cron = {}, offset = {} {}", dumpCron, offsetValue, offsetUnit);
        } else if ("hour".equalsIgnoreCase(lastPart)) {
            dumpCron = "0 1 * * * *"; // Every hour at minute 0
            offsetValue = 1;
            offsetUnit = "hours";
            log.info("Inferred from time key format (ends with 'hour'): cron = {}, offset = {} {}", dumpCron, offsetValue, offsetUnit);
        } else {
            // Default based on common case (minute-based)
            dumpCron = "1 * * * * *";
            offsetValue = 1;
            offsetUnit = "minutes";
            log.warn("Could not infer from time key format ending with '{}'. Using defaults: cron = {}, offset = {} {}", lastPart, dumpCron, offsetValue, offsetUnit);
        }
    }

    /**
     * Gets the last part of the time key format (e.g., "minute" or "hour")
     */
    private String getLastPartOfTimeKeyFormat() {
        if (timeKeyFormat == null || timeKeyFormat.isEmpty()) {
            return "";
        }
        String[] parts = timeKeyFormat.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1].trim() : "";
    }

    /**
     * Scheduled job that runs to dump analytics data from Redis to database
     * Looks for key `analytics:t_key` of current timestamp - inferred offset
     * Cron and offset are automatically inferred from analytics.time.key.format:
     * - If format ends with "minute": cron = "1 * * * * *", offset = "1 minutes"
     * - If format ends with "hour": cron = "0 1 * * * *", offset = "1 hours"
     * The inferred offset is used for calculating the target time.
     */
    @Scheduled(cron = "1 * * * * *")
    public void dumpAnalytics() {
        log.info("Starting analytics dump job");
        
        // Calculate timestamp with configured offset
        OffsetDateTime targetTime = calculateTargetTime();
        
        // Generate t_key for target time
        String tKey = analyticsService.generateTimeKey(targetTime);
        String redisKey = "analytics:" + tKey;
        
        log.info("Dumping analytics for time key: {}", tKey);
        
        // Check if the key exists in Redis
        redisTemplate.hasKey(redisKey)
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) {
                    log.info("Found analytics data for key: {}", redisKey);
                    // Dump the data
                    return analyticsService.dumpAnalyticsForTimeKey(tKey)
                        .doOnSuccess(v -> log.info("Successfully dumped analytics for time key: {}", tKey))
                        .doOnError(e -> log.error("Error dumping analytics for time key: {}", tKey, e));
                } else {
                    log.info("No analytics data found for key: {}", redisKey);
                    return Mono.empty();
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                result -> log.info("Analytics dump job completed"),
                error -> log.error("Analytics dump job failed", error)
            );
    }

    /**
     * Calculates the target time by subtracting the configured offset from current time
     * 
     * @return OffsetDateTime with the configured offset subtracted
     */
    private OffsetDateTime calculateTargetTime() {
        OffsetDateTime now = OffsetDateTime.now();
        
        switch (offsetUnit.toLowerCase()) {
            case "hours":
                return now.minus(offsetValue, ChronoUnit.HOURS);
            case "minutes":
                return now.minus(offsetValue, ChronoUnit.MINUTES);
            case "seconds":
                return now.minus(offsetValue, ChronoUnit.SECONDS);
            default:
                log.warn("Unknown offset unit: {}. Defaulting to hours.", offsetUnit);
                return now.minus(offsetValue, ChronoUnit.HOURS);
        }
    }

    /**
     * Helper method to get all analytics keys (for testing/debugging)
     */
    public Mono<Set<String>> getAllAnalyticsKeys() {
        return redisTemplate.keys("analytics:*")
            .collect(java.util.stream.Collectors.toSet())
            .subscribeOn(Schedulers.boundedElastic());
    }
}

