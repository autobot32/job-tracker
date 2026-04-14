package com.atakant.emailtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int maxRunsPerDay,
        int maxLlmEmailsPerRun,
        int maxLlmEmailsPerDay,
        String zoneId
) {
    public RateLimitProperties {
        if (maxRunsPerDay <= 0) {
            throw new IllegalArgumentException("app.rate-limit.max-runs-per-day must be positive");
        }
        if (maxLlmEmailsPerRun <= 0) {
            throw new IllegalArgumentException("app.rate-limit.max-llm-emails-per-run must be positive");
        }
        if (maxLlmEmailsPerDay <= 0) {
            throw new IllegalArgumentException("app.rate-limit.max-llm-emails-per-day must be positive");
        }
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("app.rate-limit.zone-id must be set");
        }
    }

    public RateLimitProperties() {
        this(true, 2, 10, 20, "UTC");
    }
}
