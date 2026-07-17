package com.nebulax.speed_violation_service.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Origem da captura (header x-origin, case-sensitive).")
public enum CaptureOrigin {
    @Schema(description = "Equipamento fixo (radar estático).")
    FIXED,
    @Schema(description = "Equipamento móvel (viatura).")
    MOBILE,
    @Schema(description = "Equipamento portátil (manual).")
    HANDHELD
}
