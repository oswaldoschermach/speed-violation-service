package com.velsis.speed_violation_service.domain.model;

import java.time.Instant;

public record EvaluateViolationCommand(
        String licensePlate,
        int measuredSpeed,
        int speedLimit,
        String equipmentId,
        Instant captureTimestamp,
        CaptureOrigin origin
) {
}
