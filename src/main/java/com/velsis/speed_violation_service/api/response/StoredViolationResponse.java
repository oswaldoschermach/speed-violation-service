package com.velsis.speed_violation_service.api.response;

import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Infração persistida, retornada na consulta por placa.")
public record StoredViolationResponse(
        @Schema(description = "Placa do veículo.", example = "ABC1D23")
        String licensePlate,

        @Schema(description = "Identificador do equipamento.", example = "RAD-CWB-001")
        String equipmentId,

        @Schema(description = "Velocidade medida (km/h).", example = "92")
        int measuredSpeed,

        @Schema(description = "Velocidade considerada após tolerância (km/h).", example = "85")
        int consideredSpeed,

        @Schema(description = "Limite regulamentado (km/h).", example = "60")
        int speedLimit,

        @Schema(description = "Percentual de excesso.", example = "41.67")
        BigDecimal excessPercentage,

        @Schema(description = "Gravidade da infração.", example = "SERIOUS")
        ViolationSeverity severity,

        @Schema(description = "Código CTB.", example = "218-II")
        String ctbCode,

        @Schema(description = "Momento da captura (UTC).", example = "2026-06-08T14:30:00Z")
        Instant captureTimestamp,

        @Schema(description = "Momento do processamento (UTC).", example = "2026-06-08T14:30:05Z")
        Instant processedAt,

        @Schema(description = "Origem da captura.", example = "FIXED")
        CaptureOrigin origin
) {
}
