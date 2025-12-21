package com.example.tinyurl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenRequest {
    private String url;
    private String shortUrl; // Optional custom short URL
    private OffsetDateTime expiry; // Optional expiry timestamp with timezone
}

