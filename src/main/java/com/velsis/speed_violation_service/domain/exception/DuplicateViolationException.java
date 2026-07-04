package com.velsis.speed_violation_service.domain.exception;

import lombok.Getter;

import java.time.Instant;

@Getter
public class DuplicateViolationException extends RuntimeException {

    private final String licensePlate;
    private final String equipmentId;
    private final Instant captureTimestamp;

    public DuplicateViolationException(String licensePlate, String equipmentId, Instant captureTimestamp) {
        super("Violation already registered for this capture");
        this.licensePlate = licensePlate;
        this.equipmentId = equipmentId;
        this.captureTimestamp = captureTimestamp;
    }
}
