package com.nebulax.speed_violation_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "speed-violation.capture")
public record CaptureProperties(
        Duration futureTolerance
) {
    public CaptureProperties {
        if (futureTolerance == null || futureTolerance.isNegative()) {
            futureTolerance = Duration.ZERO;
        }
    }
}
