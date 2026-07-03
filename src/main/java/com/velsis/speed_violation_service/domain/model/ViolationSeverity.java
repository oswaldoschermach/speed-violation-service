package com.velsis.speed_violation_service.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Gravidade da infração (Art. 218 CTB).")
public enum ViolationSeverity {

    @Schema(description = "Excesso até 20% — código 218-I.")
    MEDIUM("218-I"),
    @Schema(description = "Excesso acima de 20% até 50% — código 218-II.")
    SERIOUS("218-II"),
    @Schema(description = "Excesso acima de 50% — código 218-III.")
    VERY_SERIOUS("218-III");

    private final String ctbCode;

    ViolationSeverity(String ctbCode) {
        this.ctbCode = ctbCode;
    }

    public String getCtbCode() {
        return ctbCode;
    }
}
