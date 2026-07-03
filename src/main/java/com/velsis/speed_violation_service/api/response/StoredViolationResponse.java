package com.velsis.speed_violation_service.api.response;

import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;

import java.math.BigDecimal;
import java.time.Instant;

public record StoredViolationResponse(
        String licensePlate,
        String equipmentId,
        int measuredSpeed,
        int consideredSpeed,
        int speedLimit,
        BigDecimal excessPercentage,
        ViolationSeverity severity,
        String ctbCode,
        Instant captureTimestamp,
        Instant processedAt,
        CaptureOrigin origin
) {
}
