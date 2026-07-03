package com.velsis.speed_violation_service.api.mapper;

import com.velsis.speed_violation_service.api.request.EvaluateViolationRequest;
import com.velsis.speed_violation_service.api.response.StoredViolationResponse;
import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.model.EvaluateViolationCommand;
import com.velsis.speed_violation_service.persistence.entity.Violation;

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
