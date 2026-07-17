package com.nebulax.speed_violation_service.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LicensePlateValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC1234", "XYZ9876", "ABC1D23", "QWE9A87"})
    @DisplayName("aceita formatos antigo e Mercosul")
    void shouldAcceptValidPlates(String plate) {
        var sample = new PlateSample(plate);

        assertThat(validator.validate(sample)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc1234",
            "AB1234",
            "ABCD123",
            "ABC12D3",
            "ABC1D234",
            "1234ABC",
            "ABC-1234",
            "ABC 1234"
    })
    @DisplayName("rejeita placas fora dos formatos aceitos")
    void shouldRejectInvalidPlates(String plate) {
        var sample = new PlateSample(plate);

        assertThat(validator.validate(sample))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Invalid license plate format");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("delega ausência ou branco para @NotBlank")
    void shouldDelegateBlankValuesToNotBlank(String plate) {
        var sample = new NotBlankPlateSample(plate);

        assertThat(validator.validate(sample)).isNotEmpty();
        assertThat(validator.validate(sample))
                .noneMatch(v -> v.getConstraintDescriptor().getAnnotation().annotationType()
                        .equals(ValidLicensePlate.class));
    }

    private record PlateSample(@ValidLicensePlate String licensePlate) {
    }

    private record NotBlankPlateSample(
            @jakarta.validation.constraints.NotBlank @ValidLicensePlate String licensePlate
    ) {
    }
}
