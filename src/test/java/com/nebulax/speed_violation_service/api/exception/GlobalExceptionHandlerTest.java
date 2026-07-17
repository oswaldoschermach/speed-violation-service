package com.nebulax.speed_violation_service.api.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.nebulax.speed_violation_service.api.request.EvaluateViolationRequest;
import com.nebulax.speed_violation_service.domain.exception.DuplicateViolationException;
import com.nebulax.speed_violation_service.domain.exception.FutureCaptureTimestampException;
import com.nebulax.speed_violation_service.domain.model.CaptureOrigin;
import com.nebulax.speed_violation_service.support.TestTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag(TestTags.UNIT)
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final Instant FIXED = Instant.parse("2026-06-08T14:30:00Z");
    private static final MethodParameter METHOD_PARAMETER;

    static {
        try {
            METHOD_PARAMETER = new MethodParameter(
                    GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleMethod"), -1);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Mock
    private Clock clock;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(clock);
        when(clock.instant()).thenReturn(FIXED);
    }

    @Test
    @DisplayName("body validation sem field error retorna INVALID_REQUEST")
    void shouldHandleBodyValidationWithoutFieldError() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        var exception = new MethodArgumentNotValidException(METHOD_PARAMETER, bindingResult);

        var response = handler.handleBodyValidation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("body validation com target desconhecido usa log genérico")
    void shouldHandleBodyValidationForUnknownTarget() throws Exception {
        var bindingResult = new BeanPropertyBindingResult("plain", "target");
        bindingResult.addError(new FieldError("target", "field", "invalid"));
        var exception = new MethodArgumentNotValidException(METHOD_PARAMETER, bindingResult);

        var response = handler.handleBodyValidation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("mapeia campos do body para ErrorCode específico")
    void shouldMapBodyFieldErrorsToErrorCodes() throws Exception {
        assertBodyFieldError("measuredSpeed", ErrorCode.INVALID_MEASURED_SPEED);
        assertBodyFieldError("speedLimit", ErrorCode.INVALID_SPEED_LIMIT);
        assertBodyFieldError("equipmentId", ErrorCode.INVALID_EQUIPMENT_ID);
        assertBodyFieldError("captureTimestamp", ErrorCode.INVALID_CAPTURE_TIMESTAMP);
        assertBodyFieldError("unknownField", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("parameter validation com FieldError mapeia licensePlate")
    void shouldHandleParameterValidationWithFieldError() {
        var fieldError = new FieldError("violation", "licensePlate", "invalid");
        var validationResult = mock(ParameterValidationResult.class);
        when(validationResult.getResolvableErrors()).thenReturn(List.of(fieldError));
        var exception = mock(HandlerMethodValidationException.class);
        when(exception.getAllValidationResults()).thenReturn(List.of(validationResult));

        var response = handler.handleParameterValidation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_LICENSE_PLATE);
    }

    @Test
    @DisplayName("parameter validation sem FieldError retorna INVALID_REQUEST")
    void shouldHandleParameterValidationWithoutFieldError() {
        var exception = mock(HandlerMethodValidationException.class);
        when(exception.getAllValidationResults()).thenReturn(List.of());

        var response = handler.handleParameterValidation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("constraint violation em parâmetro não-placa retorna INVALID_REQUEST")
    void shouldHandleConstraintViolationForNonLicensePlateProperty() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("evaluate.origin");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("invalid");

        var response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)));

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("header ausente que não é x-origin retorna INVALID_REQUEST")
    void shouldHandleMissingNonOriginHeader() {
        var exception = new MissingRequestHeaderException("Authorization", METHOD_PARAMETER);

        var response = handler.handleMissingHeader(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("x-origin inválido retorna INVALID_ORIGIN")
    void shouldHandleInvalidOriginTypeMismatch() {
        var exception = new MethodArgumentTypeMismatchException(
                "INVALID", CaptureOrigin.class, "x-origin", METHOD_PARAMETER, null);

        var response = handler.handleTypeMismatch(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ORIGIN);
    }

    @Test
    @DisplayName("parâmetro inválido genérico retorna INVALID_REQUEST")
    void shouldHandleGenericTypeMismatch() {
        var exception = new MethodArgumentTypeMismatchException(
                "x", Integer.class, "page", METHOD_PARAMETER, null);

        var response = handler.handleTypeMismatch(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("corpo malformado sem captureTimestamp retorna INVALID_REQUEST")
    void shouldHandleMalformedRequestBody() {
        var exception = new HttpMessageNotReadableException("Unexpected end of JSON", (Throwable) null, null);

        var response = handler.handleUnreadableMessage(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("detecta captureTimestamp inválido pela mensagem da exceção")
    void shouldDetectInvalidCaptureTimestampFromMessage() {
        var exception = new HttpMessageNotReadableException(
                "Cannot deserialize captureTimestamp", (Throwable) null, null);

        var response = handler.handleUnreadableMessage(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CAPTURE_TIMESTAMP);
    }

    @Test
    @DisplayName("detecta captureTimestamp inválido via InvalidFormatException")
    void shouldDetectInvalidCaptureTimestampFromInvalidFormatException() {
        var invalidFormat = mock(InvalidFormatException.class);
        var reference = mock(com.fasterxml.jackson.databind.JsonMappingException.Reference.class);
        when(reference.getFieldName()).thenReturn("captureTimestamp");
        when(invalidFormat.getPath()).thenReturn(List.of(reference));
        var exception = new HttpMessageNotReadableException("bad json", invalidFormat, null);

        var response = handler.handleUnreadableMessage(exception);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CAPTURE_TIMESTAMP);
    }

    @Test
    @DisplayName("erro inesperado retorna 500 INTERNAL_ERROR")
    void shouldHandleUnexpectedException() {
        var response = handler.handleUnexpected(new IllegalStateException("boom"));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("mapeia exceções de domínio e HTTP conhecidas")
    void shouldHandleKnownDomainAndHttpExceptions() {
        var future = new FutureCaptureTimestampException("ABC1D23", "RAD-001", FIXED);
        assertError(
                handler.handleFutureCaptureTimestamp(future),
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_CAPTURE_TIMESTAMP);

        var duplicate = new DuplicateViolationException("ABC1D23", "RAD-001", FIXED);
        assertError(
                handler.handleDuplicateViolation(duplicate),
                HttpStatus.CONFLICT,
                ErrorCode.DUPLICATE_VIOLATION);

        assertError(
                handler.handleMissingParameter(new MissingServletRequestParameterException("licensePlate", "String")),
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_LICENSE_PLATE);

        assertError(
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH")),
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED);

        assertError(
                handler.handleNotFound(new NoResourceFoundException(null, "/missing")),
                HttpStatus.NOT_FOUND,
                ErrorCode.NOT_FOUND);

        assertError(
                handler.handleUnsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain")),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    private void assertBodyFieldError(String field, ErrorCode expected) throws Exception {
        var request = new EvaluateViolationRequest("ABC1D23", 92, 60, "RAD-CWB-001", FIXED);
        var bindingResult = new BeanPropertyBindingResult(request, "request");
        bindingResult.addError(new FieldError("request", field, "invalid"));
        var exception = new MethodArgumentNotValidException(METHOD_PARAMETER, bindingResult);

        var response = handler.handleBodyValidation(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(expected);
    }

  @SuppressWarnings("unused")
    private void sampleMethod() {
    }

    private static void assertError(
            ResponseEntity<ApiErrorResponse> response,
            HttpStatus status,
            ErrorCode errorCode) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(errorCode);
        assertThat(response.getBody().timestamp()).isEqualTo(FIXED);
    }
}
