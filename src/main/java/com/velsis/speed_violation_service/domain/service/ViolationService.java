package com.velsis.speed_violation_service.domain.service;

import com.velsis.speed_violation_service.domain.exception.DuplicateViolationException;
import com.velsis.speed_violation_service.api.mapper.ViolationMapper;
import com.velsis.speed_violation_service.api.response.EvaluateViolationResponse;
import com.velsis.speed_violation_service.api.response.StoredViolationResponse;
import com.velsis.speed_violation_service.api.response.ViolationResponse;
import com.velsis.speed_violation_service.domain.model.EvaluateViolationCommand;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import com.velsis.speed_violation_service.persistence.entity.Violation;
import com.velsis.speed_violation_service.persistence.repository.ViolationRepository;
import com.velsis.speed_violation_service.persistence.ViolationPersistenceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ViolationService {

    private final ViolationEvaluationService evaluationService;
    private final ViolationRepository violationRepository;
    private final ViolationPersistenceService violationPersistenceService;
    private final Clock clock;

    public ViolationService(
            ViolationEvaluationService evaluationService,
            ViolationRepository violationRepository,
            ViolationPersistenceService violationPersistenceService,
            Clock clock) {
        this.evaluationService = evaluationService;
        this.violationRepository = violationRepository;
        this.violationPersistenceService = violationPersistenceService;
        this.clock = clock;
    }

    public EvaluateViolationResponse evaluate(EvaluateViolationCommand command) {
        Instant processedAt = clock.instant();
        int consideredSpeed = evaluationService.calculateConsideredSpeed(
                command.measuredSpeed(), command.speedLimit());
        BigDecimal excessPercentage = evaluationService.calculateExcessPercentage(
                consideredSpeed, command.speedLimit());

        if (!evaluationService.hasViolation(consideredSpeed, command.speedLimit())) {
            return buildResponse(command, consideredSpeed, excessPercentage, null, processedAt);
        }

        ViolationSeverity severity = evaluationService.determineSeverity(excessPercentage);
        Violation violation = buildViolation(command, consideredSpeed, excessPercentage, severity, processedAt);
        saveViolation(violation, command);

        return buildResponse(command, consideredSpeed, excessPercentage, severity, processedAt);
    }

    @Transactional(readOnly = true)
    public List<StoredViolationResponse> findByLicensePlate(String licensePlate) {
        return violationRepository.findByLicensePlateOrderByProcessedAtDesc(licensePlate).stream()
                .map(ViolationMapper::toStoredResponse)
                .toList();
    }

    private Violation buildViolation(
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

    private EvaluateViolationResponse buildResponse(
            EvaluateViolationCommand command,
            int consideredSpeed,
            BigDecimal excessPercentage,
            ViolationSeverity severity,
            Instant processedAt) {
        ViolationResponse violation = severity == null
                ? null
                : new ViolationResponse(severity, severity.getCtbCode());

        return new EvaluateViolationResponse(
                command.licensePlate(),
                command.equipmentId(),
                command.measuredSpeed(),
                consideredSpeed,
                command.speedLimit(),
                excessPercentage,
                severity != null,
                violation,
                processedAt);
    }
}
