package com.velsis.speed_violation_service.persistence.entity;

import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "violations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "license_plate", nullable = false, length = 7)
    private String licensePlate;

    @Column(name = "equipment_id", nullable = false, length = 50)
    private String equipmentId;

    @Column(name = "measured_speed", nullable = false)
    private int measuredSpeed;

    @Column(name = "considered_speed", nullable = false)
    private int consideredSpeed;

    @Column(name = "speed_limit", nullable = false)
    private int speedLimit;

    @Column(name = "excess_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal excessPercentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViolationSeverity severity;

    @Column(name = "ctb_code", nullable = false, length = 10)
    private String ctbCode;

    @Column(name = "capture_timestamp", nullable = false)
    private Instant captureTimestamp;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaptureOrigin origin;

    @Builder
    private Violation(
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
            CaptureOrigin origin) {
        this.licensePlate = Objects.requireNonNull(licensePlate);
        this.equipmentId = Objects.requireNonNull(equipmentId);
        this.measuredSpeed = measuredSpeed;
        this.consideredSpeed = consideredSpeed;
        this.speedLimit = speedLimit;
        this.excessPercentage = Objects.requireNonNull(excessPercentage);
        this.severity = Objects.requireNonNull(severity);
        this.ctbCode = Objects.requireNonNull(ctbCode);
        this.captureTimestamp = Objects.requireNonNull(captureTimestamp);
        this.processedAt = Objects.requireNonNull(processedAt);
        this.origin = Objects.requireNonNull(origin);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Violation that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
