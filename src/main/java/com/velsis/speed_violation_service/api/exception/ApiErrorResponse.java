package com.velsis.speed_violation_service.api.exception;

import java.time.Instant;

public record ApiErrorResponse(
        ErrorCode error,
        String message,
        Instant timestamp
) {
}
