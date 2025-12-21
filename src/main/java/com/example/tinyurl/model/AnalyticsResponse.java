package com.example.tinyurl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private OffsetDateTime time;
    private Long count;
}

