package com.velsis.speed_violation_service.api.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.velsis.speed_violation_service.api.request.EvaluateViolationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError == null) {
            return buildResponse(ErrorCode.INVALID_REQUEST, "Invalid request", HttpStatus.BAD_REQUEST);
        }

        ErrorCode errorCode = resolveBodyErrorCode(fieldError);
        Object target = exception.getBindingResult().getTarget();
        if (target instanceof EvaluateViolationRequest request) {
            log.warn("Validation error: licensePlate={}, equipmentId={}, field={}, code={}",
                    request.licensePlate(), request.equipmentId(), fieldError.getField(), errorCode);
        } else {
            log.warn("Validation error: field={}, code={}", fieldError.getField(), errorCode);
        }
        return buildResponse(errorCode, fieldError.getDefaultMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException exception) {
        var violation = exception.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .findFirst()
                .orElse(null);

        if (violation instanceof FieldError fieldError) {
            ErrorCode errorCode = resolveQueryParamErrorCode(fieldError);
            log.warn("Validation error: parameter={}, code={}", fieldError.getField(), errorCode);
            return buildResponse(errorCode, fieldError.getDefaultMessage(), HttpStatus.BAD_REQUEST);
        }

        return buildResponse(ErrorCode.INVALID_REQUEST, "Invalid request", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        ConstraintViolation<?> violation = exception.getConstraintViolations().iterator().next();
        String property = violation.getPropertyPath().toString();
        ErrorCode errorCode = resolveParameterErrorCode(property);

        log.warn("Validation error: parameter={}, code={}", property, errorCode);
        return buildResponse(errorCode, violation.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        if ("x-origin".equalsIgnoreCase(exception.getHeaderName())) {
            log.warn("Missing required header: x-origin");
            return buildResponse(ErrorCode.INVALID_ORIGIN, "Missing x-origin header", HttpStatus.BAD_REQUEST);
        }

        return buildResponse(ErrorCode.INVALID_REQUEST, "Missing required header", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        if ("origin".equals(exception.getName()) || "x-origin".equals(exception.getName())) {
            log.warn("Invalid x-origin value");
            return buildResponse(ErrorCode.INVALID_ORIGIN, "Invalid x-origin value", HttpStatus.BAD_REQUEST);
        }

        return buildResponse(ErrorCode.INVALID_REQUEST, "Invalid request parameter", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        if (isInvalidCaptureTimestamp(exception)) {
            log.warn("Invalid captureTimestamp format");
            return buildResponse(
                    ErrorCode.INVALID_CAPTURE_TIMESTAMP,
                    "Invalid capture timestamp format",
                    HttpStatus.BAD_REQUEST);
        }

        log.warn("Malformed request body");
        return buildResponse(ErrorCode.INVALID_REQUEST, "Malformed request body", HttpStatus.BAD_REQUEST);
    }

    private boolean isInvalidCaptureTimestamp(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            return invalidFormat.getPath().stream()
                    .anyMatch(reference -> "captureTimestamp".equals(reference.getFieldName()));
        }

        String message = exception.getMessage();
        return message != null && message.contains("captureTimestamp");
    }

    @ExceptionHandler(DuplicateViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateViolation(DuplicateViolationException exception) {
        log.warn("Duplicate violation: licensePlate={}, equipmentId={}, captureTimestamp={}",
                exception.getLicensePlate(), exception.getEquipmentId(), exception.getCaptureTimestamp());
        return buildResponse(
                ErrorCode.DUPLICATE_VIOLATION,
                exception.getMessage(),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return buildResponse(ErrorCode.INTERNAL_ERROR, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ErrorCode resolveBodyErrorCode(FieldError fieldError) {
        return switch (fieldError.getField()) {
            case "licensePlate" -> ErrorCode.INVALID_LICENSE_PLATE;
            case "measuredSpeed" -> ErrorCode.INVALID_MEASURED_SPEED;
            case "speedLimit" -> ErrorCode.INVALID_SPEED_LIMIT;
            case "equipmentId" -> ErrorCode.INVALID_EQUIPMENT_ID;
            case "captureTimestamp" -> ErrorCode.INVALID_CAPTURE_TIMESTAMP;
            default -> ErrorCode.INVALID_REQUEST;
        };
    }

    private ErrorCode resolveQueryParamErrorCode(FieldError fieldError) {
        return resolveParameterErrorCode(fieldError.getField());
    }

    private ErrorCode resolveParameterErrorCode(String property) {
        if (property.endsWith("licensePlate")) {
            return ErrorCode.INVALID_LICENSE_PLATE;
        }
        return ErrorCode.INVALID_REQUEST;
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            ErrorCode errorCode,
            String message,
            HttpStatus status) {
        Instant timestamp = clock.instant();
        return ResponseEntity.status(status).body(new ApiErrorResponse(errorCode, message, timestamp));
    }
}
