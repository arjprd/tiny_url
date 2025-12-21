package com.example.tinyurl.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "short_url_click_analytics", indexes = {
    @Index(name = "idx_time", columnList = "time"),
    @Index(name = "idx_url_id", columnList = "url_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ShortUrlClickAnalytics {

    @EmbeddedId
    private ShortUrlClickAnalyticsId id;

    @Column(name = "count", nullable = false)
    private Long count;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", insertable = false, updatable = false, 
                foreignKey = @ForeignKey(name = "fk_short_url_click_analytics_url_id"))
    private ShortUrl url;

    public ShortUrlClickAnalytics(OffsetDateTime time, Long urlId, Long count) {
        this.id = new ShortUrlClickAnalyticsId(time, urlId);
        this.count = count;
    }

    public OffsetDateTime getTime() {
        return id != null ? id.getTime() : null;
    }

    public Long getUrlId() {
        return id != null ? id.getUrlId() : null;
    }

    public void setTime(OffsetDateTime time) {
        if (id == null) {
            id = new ShortUrlClickAnalyticsId();
        }
        id.setTime(time);
    }

    public void setUrlId(Long urlId) {
        if (id == null) {
            id = new ShortUrlClickAnalyticsId();
        }
        id.setUrlId(urlId);
    }
}

