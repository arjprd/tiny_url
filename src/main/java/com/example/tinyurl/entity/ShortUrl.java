package com.example.tinyurl.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "short_url", indexes = {
    @Index(name = "idx_long_url_hash", columnList = "long_url_hash"),
    @Index(name = "idx_owner", columnList = "owner")
})
@Getter
@Setter
@NoArgsConstructor
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Column(name = "long_url_hash", nullable = false, length = 64)
    private String longUrlHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expiry", nullable = true)
    private OffsetDateTime expiry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", foreignKey = @ForeignKey(name = "fk_short_url_owner"))
    private User owner;

    public ShortUrl(String longUrl, String longUrlHash) {
        this.longUrl = longUrl;
        this.longUrlHash = longUrlHash;
    }

    public ShortUrl(String longUrl, String longUrlHash, User owner) {
        this.longUrl = longUrl;
        this.longUrlHash = longUrlHash;
        this.owner = owner;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}

