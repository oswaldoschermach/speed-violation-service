package com.nebulax.speed_violation_service.api.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluateViolationRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("request válido não gera violações")
    void shouldAcceptValidRequest() {
        var request = validRequest();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("rejeita placa inválida")
    void shouldRejectInvalidLicensePlate() {
        var request = withPlate("INVALID");

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("licensePlate");
    }

    @Test
    @DisplayName("rejeita velocidade medida nula, zero ou negativa")
    void shouldRejectInvalidMeasuredSpeed() {
        assertThat(validator.validate(validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), null, r.speedLimit(), r.equipmentId(), r.captureTimestamp()
        )))).isNotEmpty();

        assertThat(validator.validate(validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), 0, r.speedLimit(), r.equipmentId(), r.captureTimestamp()
        )))).isNotEmpty();

        assertThat(validator.validate(validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), -10, r.speedLimit(), r.equipmentId(), r.captureTimestamp()
        )))).isNotEmpty();
    }

    @Test
    @DisplayName("rejeita limite nulo, zero ou negativo")
    void shouldRejectInvalidSpeedLimit() {
        assertThat(validator.validate(validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), r.measuredSpeed(), null, r.equipmentId(), r.captureTimestamp()
        )))).isNotEmpty();

        assertThat(validator.validate(validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), r.measuredSpeed(), 0, r.equipmentId(), r.captureTimestamp()
        )))).isNotEmpty();
    }

    @Test
    @DisplayName("rejeita equipmentId ausente ou em branco")
    void shouldRejectBlankEquipmentId() {
        assertThat(validator.validate(validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), r.measuredSpeed(), r.speedLimit(), "", r.captureTimestamp()
        )))).isNotEmpty();

        assertThat(validator.validate(validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), r.measuredSpeed(), r.speedLimit(), null, r.captureTimestamp()
        )))).isNotEmpty();
    }

    @Test
    @DisplayName("rejeita captureTimestamp nulo")
    void shouldRejectNullCaptureTimestamp() {
        assertThat(validator.validate(validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), r.measuredSpeed(), r.speedLimit(), r.equipmentId(), null
        )))).isNotEmpty();
    }

    @Test
    @DisplayName("aceita captureTimestamp presente")
    void shouldAcceptPresentCaptureTimestamp() {
        var request = validRequestWith(r -> new EvaluateViolationRequest(
                r.licensePlate(), r.measuredSpeed(), r.speedLimit(), r.equipmentId(), Instant.now()
        ));

        assertThat(validator.validate(request)).isEmpty();
    }

    private EvaluateViolationRequest validRequest() {
        return new EvaluateViolationRequest(
                "ABC1D23",
                92,
                60,
                "RAD-CWB-001",
                Instant.parse("2026-06-08T14:30:00Z")
        );
    }

    private EvaluateViolationRequest withPlate(String licensePlate) {
        EvaluateViolationRequest base = validRequest();
        return new EvaluateViolationRequest(
                licensePlate,
                base.measuredSpeed(),
                base.speedLimit(),
                base.equipmentId(),
                base.captureTimestamp()
        );
    }

    private EvaluateViolationRequest validRequestWith(
            java.util.function.Function<EvaluateViolationRequest, EvaluateViolationRequest> mutator
    ) {
        return mutator.apply(validRequest());
    }
}
