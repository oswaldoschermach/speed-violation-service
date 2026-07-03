package com.velsis.speed_violation_service.api.request;

import com.velsis.speed_violation_service.api.validation.ValidLicensePlate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record EvaluateViolationRequest(
        @NotBlank(message = "License plate is required")
        @ValidLicensePlate
        String licensePlate,

        @NotNull(message = "Measured speed is required")
        @Positive(message = "Measured speed must be greater than zero")
        Integer measuredSpeed,

        @NotNull(message = "Speed limit is required")
        @Positive(message = "Speed limit must be greater than zero")
        Integer speedLimit,

        @NotBlank(message = "Equipment id is required")
        String equipmentId,

        @NotNull(message = "Capture timestamp is required")
        @PastOrPresent(message = "Capture timestamp cannot be in the future")
        Instant captureTimestamp
) {
}
