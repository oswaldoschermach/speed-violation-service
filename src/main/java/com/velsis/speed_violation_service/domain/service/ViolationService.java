package com.velsis.speed_violation_service.domain.service;

import com.velsis.speed_violation_service.domain.exception.DuplicateViolationException;
import com.velsis.speed_violation_service.api.mapper.ViolationMapper;
import com.velsis.speed_violation_service.api.response.EvaluateViolationResponse;
import com.velsis.speed_violation_service.api.response.StoredViolationResponse;
import com.velsis.speed_violation_service.domain.model.EvaluateViolationCommand;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import com.velsis.speed_violation_service.persistence.entity.Violation;
import com.velsis.speed_violation_service.persistence.repository.ViolationRepository;
import com.velsis.speed_violation_service.persistence.ViolationPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ViolationService {

    private final ViolationEvaluationService evaluationService;
    private final ViolationRepository violationRepository;
    private final ViolationPersistenceService violationPersistenceService;
    private final Clock clock;

    public EvaluateViolationResponse evaluate(EvaluateViolationCommand command) {
        Instant processedAt = clock.instant();
        int consideredSpeed = evaluationService.calculateConsideredSpeed(
                command.measuredSpeed(), command.speedLimit());
        BigDecimal excessPercentage = evaluationService.calculateExcessPercentage(
                consideredSpeed, command.speedLimit());

        ViolationSeverity severity = evaluationService.hasViolation(consideredSpeed, command.speedLimit())
                ? ViolationSeverity.fromExcessPercentage(excessPercentage)
                : null;

        if (severity != null) {
            Violation violation = ViolationMapper.toEntity(
                    command, consideredSpeed, excessPercentage, severity, processedAt);
            saveViolation(violation, command);
        }

        return ViolationMapper.toEvaluateResponse(
                command, consideredSpeed, excessPercentage, severity, processedAt);
    }

    @Transactional(readOnly = true)
    public List<StoredViolationResponse> findByLicensePlate(String licensePlate) {
        return violationRepository.findByLicensePlateOrderByProcessedAtDesc(licensePlate).stream()
                .map(ViolationMapper::toStoredResponse)
                .toList();
    }

    private void saveViolation(Violation violation, EvaluateViolationCommand command) {
        if (violationRepository.existsByLicensePlateAndEquipmentIdAndCaptureTimestamp(
                command.licensePlate(), command.equipmentId(), command.captureTimestamp())) {
            throw duplicateViolationException(command);
        }

        try {
            violationPersistenceService.persist(violation);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateViolationException(command);
        }
    }

    private DuplicateViolationException duplicateViolationException(EvaluateViolationCommand command) {
        return new DuplicateViolationException(
                command.licensePlate(), command.equipmentId(), command.captureTimestamp());
    }
}
