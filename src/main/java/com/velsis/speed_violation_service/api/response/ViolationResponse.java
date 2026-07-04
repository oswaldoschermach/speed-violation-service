package com.velsis.speed_violation_service.api.response;

import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Classificação da infração conforme Art. 218 do CTB.")
public record ViolationResponse(
        @Schema(
                description = "Gravidade da infração.",
                example = "SERIOUS",
                allowableValues = {"MEDIUM", "SERIOUS", "VERY_SERIOUS"})
        ViolationSeverity severity,

        @Schema(description = "Código do CTB correspondente à gravidade.", example = "218-II")
        String ctbCode
) {
    public static ViolationResponse of(ViolationSeverity severity) {
        return new ViolationResponse(severity, severity.getCtbCode());
    }
}
