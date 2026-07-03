package com.velsis.speed_violation_service.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class LicensePlateValidator implements ConstraintValidator<ValidLicensePlate, String> {

    private static final Pattern LEGACY_PLATE = Pattern.compile("^[A-Z]{3}\\d{4}$");
    private static final Pattern MERCOSUL_PLATE = Pattern.compile("^[A-Z]{3}\\d[A-Z]\\d{2}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return LEGACY_PLATE.matcher(value).matches() || MERCOSUL_PLATE.matcher(value).matches();
    }
}
