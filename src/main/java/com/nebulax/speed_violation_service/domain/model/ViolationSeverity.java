package com.nebulax.speed_violation_service.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Gravidade da infração (Art. 218 CTB).")
public enum ViolationSeverity {

    @Schema(description = "Excesso até 20% — código 218-I.")
    MEDIUM("218-I"),
    @Schema(description = "Excesso acima de 20% até 50% — código 218-II.")
    SERIOUS("218-II"),
    @Schema(description = "Excesso acima de 50% — código 218-III.")
    VERY_SERIOUS("218-III");

    private static final BigDecimal MEDIUM_THRESHOLD = BigDecimal.valueOf(20);
    private static final BigDecimal SERIOUS_THRESHOLD = BigDecimal.valueOf(50);

    private final String ctbCode;

    ViolationSeverity(String ctbCode) {
        this.ctbCode = ctbCode;
    }

    public static ViolationSeverity fromExcessPercentage(BigDecimal excessPercentage) {
        if (excessPercentage.compareTo(MEDIUM_THRESHOLD) <= 0) {
            return MEDIUM;
        }
        if (excessPercentage.compareTo(SERIOUS_THRESHOLD) <= 0) {
            return SERIOUS;
        }
        return VERY_SERIOUS;
    }

    public String getCtbCode() {
        return ctbCode;
    }
}
