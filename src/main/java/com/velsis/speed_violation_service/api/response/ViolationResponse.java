package com.velsis.speed_violation_service.api.response;

import com.velsis.speed_violation_service.domain.model.ViolationSeverity;

public record ViolationResponse(
        ViolationSeverity severity,
        String ctbCode
) {
}
