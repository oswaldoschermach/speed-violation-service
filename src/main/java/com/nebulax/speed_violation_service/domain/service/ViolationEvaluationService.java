package com.nebulax.speed_violation_service.domain.service;

import com.nebulax.speed_violation_service.config.ToleranceProperties;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
public class ViolationEvaluationService {

    private final ToleranceProperties tolerance;

    public int calculateConsideredSpeed(int measuredSpeed, int speedLimit) {
        if (speedLimit > tolerance.percentThresholdKmh()) {
            return (measuredSpeed * (100 - tolerance.percentMargin())) / 100;
        }
        return measuredSpeed - tolerance.kmhMargin();
    }

    public BigDecimal calculateExcessPercentage(int consideredSpeed, int speedLimit) {
        if (!hasViolation(consideredSpeed, speedLimit)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }

        return BigDecimal.valueOf((long) consideredSpeed - speedLimit)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(speedLimit), 2, RoundingMode.HALF_UP);
    }

    public boolean hasViolation(int consideredSpeed, int speedLimit) {
        return consideredSpeed > speedLimit;
    }
}
