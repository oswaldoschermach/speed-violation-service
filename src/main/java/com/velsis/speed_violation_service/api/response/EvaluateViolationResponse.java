package com.velsis.speed_violation_service.api.response;

import java.math.BigDecimal;
import java.time.Instant;

public record EvaluateViolationResponse(
        String licensePlate,
        String equipmentId,
        int measuredSpeed,
        int consideredSpeed,
        int speedLimit,
        BigDecimal excessPercentage,
        boolean hasViolation,
        ViolationResponse violation,
        Instant processedAt
) {
}
