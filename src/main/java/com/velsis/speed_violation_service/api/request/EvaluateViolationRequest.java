package com.velsis.speed_violation_service.api.request;

import com.velsis.speed_violation_service.api.validation.ValidLicensePlate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

@Schema(description = "Leitura de velocidade capturada por equipamento de fiscalização.")
public record EvaluateViolationRequest(
        @Schema(
                description = "Placa do veículo. Formatos aceitos: antigo (ABC1234) ou Mercosul (ABC1D23).",
                example = "ABC1D23",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "License plate is required")
        @ValidLicensePlate
        String licensePlate,

        @Schema(
                description = "Velocidade medida pelo equipamento, em km/h. Deve ser maior que zero.",
                example = "92",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Measured speed is required")
        @Positive(message = "Measured speed must be greater than zero")
        Integer measuredSpeed,

        @Schema(
                description = "Velocidade regulamentada da via, em km/h. Deve ser maior que zero.",
                example = "60",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Speed limit is required")
        @Positive(message = "Speed limit must be greater than zero")
        Integer speedLimit,

        @Schema(
                description = "Identificador do equipamento que registrou a leitura.",
                example = "RAD-CWB-001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Equipment id is required")
        String equipmentId,

        @Schema(
                description = "Momento da captura em ISO-8601 (UTC). Não pode ser no futuro.",
                example = "2026-06-08T14:30:00Z",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Capture timestamp is required")
        Instant captureTimestamp
) {
}
