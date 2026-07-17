package com.nebulax.speed_violation_service.api.mapper;

import com.nebulax.speed_violation_service.api.request.EvaluateViolationRequest;
import com.nebulax.speed_violation_service.api.response.EvaluateViolationResponse;
import com.nebulax.speed_violation_service.api.response.StoredViolationResponse;
import com.nebulax.speed_violation_service.api.response.ViolationResponse;
import com.nebulax.speed_violation_service.domain.model.CaptureOrigin;
import com.nebulax.speed_violation_service.domain.model.EvaluateViolationCommand;
import com.nebulax.speed_violation_service.domain.model.ViolationSeverity;
import com.nebulax.speed_violation_service.persistence.entity.Violation;

import java.math.BigDecimal;
import java.time.Instant;

public final class ViolationMapper {

    private ViolationMapper() {
    }

    public static EvaluateViolationCommand toCommand(EvaluateViolationRequest request, CaptureOrigin origin) {
        return new EvaluateViolationCommand(
                request.licensePlate(),
                request.measuredSpeed(),
                request.speedLimit(),
                request.equipmentId(),
                request.captureTimestamp(),
                origin
        );
    }

    public static Violation toEntity(
            EvaluateViolationCommand command,
            int consideredSpeed,
            BigDecimal excessPercentage,
            ViolationSeverity severity,
            Instant processedAt) {
        return Violation.builder()
                .licensePlate(command.licensePlate())
                .equipmentId(command.equipmentId())
                .measuredSpeed(command.measuredSpeed())
                .consideredSpeed(consideredSpeed)
                .speedLimit(command.speedLimit())
                .excessPercentage(excessPercentage)
                .severity(severity)
                .ctbCode(severity.getCtbCode())
                .captureTimestamp(command.captureTimestamp())
                .processedAt(processedAt)
                .origin(command.origin())
                .build();
    }

    public static EvaluateViolationResponse toEvaluateResponse(
            EvaluateViolationCommand command,
            int consideredSpeed,
            BigDecimal excessPercentage,
            ViolationSeverity severity,
            Instant processedAt) {
        ViolationResponse violation = severity != null ? ViolationResponse.of(severity) : null;

        return new EvaluateViolationResponse(
                command.licensePlate(),
                command.equipmentId(),
                command.measuredSpeed(),
                consideredSpeed,
                command.speedLimit(),
                excessPercentage,
                violation != null,
                violation,
                processedAt);
    }

    public static StoredViolationResponse toStoredResponse(Violation violation) {
        return new StoredViolationResponse(
                violation.getLicensePlate(),
                violation.getEquipmentId(),
                violation.getMeasuredSpeed(),
                violation.getConsideredSpeed(),
                violation.getSpeedLimit(),
                violation.getExcessPercentage(),
                violation.getSeverity(),
                violation.getCtbCode(),
                violation.getCaptureTimestamp(),
                violation.getProcessedAt(),
                violation.getOrigin()
        );
    }
}
