package com.velsis.speed_violation_service.api.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resposta padronizada de erro da API.")
public record ApiErrorResponse(
        @Schema(
                description = "Código estável do erro.",
                example = "INVALID_LICENSE_PLATE",
                allowableValues = {
                        "INVALID_LICENSE_PLATE",
                        "INVALID_MEASURED_SPEED",
                        "INVALID_SPEED_LIMIT",
                        "INVALID_EQUIPMENT_ID",
                        "INVALID_CAPTURE_TIMESTAMP",
                        "INVALID_ORIGIN",
                        "INVALID_REQUEST",
                        "DUPLICATE_VIOLATION",
                        "INTERNAL_ERROR"
                })
        ErrorCode error,

        @Schema(description = "Mensagem legível para o cliente.", example = "Invalid license plate format")
        String message,

        @Schema(description = "Momento do erro (UTC).", example = "2026-06-08T14:30:05Z")
        Instant timestamp
) {
}
