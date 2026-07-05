package com.velsis.speed_violation_service.domain.exception;

import lombok.Getter;

import java.time.Instant;

@Getter
public class FutureCaptureTimestampException extends RuntimeException {

    private final String licensePlate;
    private final String equipmentId;
    private final Instant captureTimestamp;

    public FutureCaptureTimestampException(String licensePlate, String equipmentId, Instant captureTimestamp) {
        super("Capture timestamp cannot be in the future");
        this.licensePlate = licensePlate;
        this.equipmentId = equipmentId;
        this.captureTimestamp = captureTimestamp;
    }
}
