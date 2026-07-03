package com.velsis.speed_violation_service.domain.service;

import com.velsis.speed_violation_service.api.exception.DuplicateViolationException;
import com.velsis.speed_violation_service.api.response.EvaluateViolationResponse;
import com.velsis.speed_violation_service.config.ToleranceProperties;
import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.model.EvaluateViolationCommand;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import com.velsis.speed_violation_service.persistence.ViolationPersistenceService;
import com.velsis.speed_violation_service.persistence.entity.Violation;
import com.velsis.speed_violation_service.persistence.repository.ViolationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-08T14:30:05Z");

    @Mock
    private ViolationRepository violationRepository;

    @Mock
    private ViolationPersistenceService violationPersistenceService;

    private ViolationService violationService;

    @BeforeEach
    void setUp() {
        ViolationEvaluationService evaluationService =
                new ViolationEvaluationService(new ToleranceProperties(7, 7, 100));
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        violationService = new ViolationService(
                evaluationService, violationRepository, violationPersistenceService, clock);
    }

    @Test
    @DisplayName("orquestra apuração com infração, persiste e monta resposta")
    void shouldEvaluatePersistAndBuildResponseWhenViolationExists() {
        EvaluateViolationCommand command = command(92, 60);
        when(violationRepository.existsByLicensePlateAndEquipmentIdAndCaptureTimestamp(
                eq("ABC1D23"), eq("RAD-CWB-001"), eq(Instant.parse("2026-06-08T14:30:00Z"))))
                .thenReturn(false);

        EvaluateViolationResponse response = violationService.evaluate(command);

        assertThat(response.licensePlate()).isEqualTo("ABC1D23");
        assertThat(response.consideredSpeed()).isEqualTo(85);
        assertThat(response.excessPercentage()).isEqualByComparingTo(new BigDecimal("41.67"));
        assertThat(response.hasViolation()).isTrue();
        assertThat(response.violation().severity()).isEqualTo(ViolationSeverity.SERIOUS);
        assertThat(response.violation().ctbCode()).isEqualTo("218-II");
        assertThat(response.processedAt()).isEqualTo(FIXED_INSTANT);

        ArgumentCaptor<Violation> captor = ArgumentCaptor.forClass(Violation.class);
        verify(violationPersistenceService).persist(captor.capture());

        Violation saved = captor.getValue();
        assertThat(saved.getLicensePlate()).isEqualTo("ABC1D23");
        assertThat(saved.getSeverity()).isEqualTo(ViolationSeverity.SERIOUS);
        assertThat(saved.getProcessedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    @DisplayName("orquestra apuração sem infração sem persistir")
    void shouldEvaluateWithoutPersistingWhenNoViolation() {
        EvaluateViolationCommand command = command(64, 60);

        EvaluateViolationResponse response = violationService.evaluate(command);

        assertThat(response.consideredSpeed()).isEqualTo(57);
        assertThat(response.excessPercentage()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(response.hasViolation()).isFalse();
        assertThat(response.violation()).isNull();
        assertThat(response.processedAt()).isEqualTo(FIXED_INSTANT);

        verify(violationRepository, never()).save(any());
        verify(violationPersistenceService, never()).persist(any());
    }

    @Test
    @DisplayName("rejeita captura duplicada antes de persistir")
    void shouldRejectDuplicateCapture() {
        EvaluateViolationCommand command = command(92, 60);
        when(violationRepository.existsByLicensePlateAndEquipmentIdAndCaptureTimestamp(
                eq("ABC1D23"), eq("RAD-CWB-001"), eq(Instant.parse("2026-06-08T14:30:00Z"))))
                .thenReturn(true);

        assertThatThrownBy(() -> violationService.evaluate(command))
                .isInstanceOf(DuplicateViolationException.class)
                .hasMessage("Violation already registered for this capture");

        verify(violationPersistenceService, never()).persist(any());
    }

    private EvaluateViolationCommand command(int measuredSpeed, int speedLimit) {
        return new EvaluateViolationCommand(
                "ABC1D23",
                measuredSpeed,
                speedLimit,
                "RAD-CWB-001",
                Instant.parse("2026-06-08T14:30:00Z"),
                CaptureOrigin.FIXED
        );
    }
}
