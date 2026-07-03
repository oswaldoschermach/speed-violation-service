package com.velsis.speed_violation_service.api.controller;

import com.velsis.speed_violation_service.api.mapper.ViolationMapper;
import com.velsis.speed_violation_service.api.request.EvaluateViolationRequest;
import com.velsis.speed_violation_service.api.response.EvaluateViolationResponse;
import com.velsis.speed_violation_service.api.response.StoredViolationResponse;
import com.velsis.speed_violation_service.api.validation.ValidLicensePlate;
import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.service.ViolationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/violations")
@Validated
public class ViolationController {

    private final ViolationService violationService;

    public ViolationController(ViolationService violationService) {
        this.violationService = violationService;
    }

    @PostMapping("/evaluate")
    public EvaluateViolationResponse evaluate(
            @RequestHeader("x-origin") CaptureOrigin origin,
            @Valid @RequestBody EvaluateViolationRequest request) {
        return violationService.evaluate(ViolationMapper.toCommand(request, origin));
    }

    @GetMapping
    public List<StoredViolationResponse> findByLicensePlate(
            @RequestParam @NotBlank(message = "License plate is required") @ValidLicensePlate String licensePlate) {
        return violationService.findByLicensePlate(licensePlate);
    }
}
