package com.example.tinyurl.repository;

import com.example.tinyurl.entity.ShortUrlClickAnalytics;
import com.example.tinyurl.entity.ShortUrlClickAnalyticsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ShortUrlClickAnalyticsRepository extends JpaRepository<ShortUrlClickAnalytics, ShortUrlClickAnalyticsId> {
    
    /**
     * Find all analytics records for a specific URL ID within a time range
     * @param urlId The URL ID to filter by
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return List of analytics records ordered by time
     */
    @Query("SELECT a FROM ShortUrlClickAnalytics a " +
           "WHERE a.id.urlId = :urlId " +
           "AND a.id.time >= :startTime " +
           "AND a.id.time <= :endTime " +
           "ORDER BY a.id.time ASC")
    List<ShortUrlClickAnalytics> findByUrlIdAndTimeRange(
        @Param("urlId") Long urlId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );
}

