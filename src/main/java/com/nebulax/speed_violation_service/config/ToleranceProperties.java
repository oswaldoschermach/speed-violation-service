package com.nebulax.speed_violation_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "speed-violation.tolerance")
public record ToleranceProperties(
        int kmhMargin,
        int percentMargin,
        int percentThresholdKmh
) {
}
