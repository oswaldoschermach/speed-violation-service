package com.velsis.speed_violation_service.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Resultado da apuração de uma leitura de velocidade.")
public record EvaluateViolationResponse(
        @Schema(description = "Placa do veículo.", example = "ABC1D23")
        String licensePlate,

        @Schema(description = "Identificador do equipamento.", example = "RAD-CWB-001")
        String equipmentId,

        @Schema(description = "Velocidade medida (km/h).", example = "92")
        int measuredSpeed,

        @Schema(description = "Velocidade considerada após tolerância legal (km/h).", example = "85")
        int consideredSpeed,

        @Schema(description = "Limite regulamentado da via (km/h).", example = "60")
        int speedLimit,

        @Schema(description = "Percentual de excesso sobre o limite. Zero quando não há infração.", example = "41.67")
        BigDecimal excessPercentage,

        @Schema(description = "Indica se houve infração após aplicar tolerância.", example = "true")
        boolean hasViolation,

        @Schema(description = "Detalhes da infração. Null quando hasViolation é false.")
        ViolationResponse violation,

        @Schema(description = "Momento do processamento (UTC).", example = "2026-06-08T14:30:05Z")
        Instant processedAt
) {
}
