package com.velsis.speed_violation_service.domain.service;

import com.velsis.speed_violation_service.config.ToleranceProperties;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ViolationEvaluationService {

    private static final BigDecimal MEDIUM_THRESHOLD = BigDecimal.valueOf(20);
    private static final BigDecimal SERIOUS_THRESHOLD = BigDecimal.valueOf(50);

    private final ToleranceProperties tolerance;

    public ViolationEvaluationService(ToleranceProperties tolerance) {
        this.tolerance = tolerance;
    }

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

        return BigDecimal.valueOf(consideredSpeed - speedLimit)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(speedLimit), 2, RoundingMode.HALF_UP);
    }

    public boolean hasViolation(int consideredSpeed, int speedLimit) {
        return consideredSpeed > speedLimit;
    }

    public ViolationSeverity determineSeverity(BigDecimal excessPercentage) {
        if (excessPercentage.compareTo(MEDIUM_THRESHOLD) <= 0) {
            return ViolationSeverity.MEDIUM;
        }
        if (excessPercentage.compareTo(SERIOUS_THRESHOLD) <= 0) {
            return ViolationSeverity.SERIOUS;
        }
        return ViolationSeverity.VERY_SERIOUS;
    }
}
