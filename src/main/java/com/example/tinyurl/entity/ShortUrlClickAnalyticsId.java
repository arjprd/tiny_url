package com.example.tinyurl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShortUrlClickAnalyticsId implements Serializable {

    @Column(name = "time", nullable = false)
    private OffsetDateTime time;

    @Column(name = "url_id", nullable = false)
    private Long urlId;
}

