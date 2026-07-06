package com.velsis.speed_violation_service.api.controller;

import com.velsis.speed_violation_service.api.exception.GlobalExceptionHandler;
import com.velsis.speed_violation_service.api.response.EvaluateViolationResponse;
import com.velsis.speed_violation_service.api.response.ViolationResponse;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import com.velsis.speed_violation_service.domain.service.ViolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ViolationController.class)
@Import(GlobalExceptionHandler.class)
class ViolationControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-08T14:30:05Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViolationService violationService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("POST /evaluate com measuredSpeed inválido retorna 400")
    void shouldRejectInvalidMeasuredSpeed() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 0,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MEASURED_SPEED"));
    }

    @Test
    @DisplayName("POST /evaluate com speedLimit inválido retorna 400")
    void shouldRejectInvalidSpeedLimit() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 92,
                                  "speedLimit": 0,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SPEED_LIMIT"));
    }

    @Test
    @DisplayName("POST /evaluate com equipmentId em branco retorna 400")
    void shouldRejectBlankEquipmentId() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 92,
                                  "speedLimit": 60,
                                  "equipmentId": "",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_EQUIPMENT_ID"));
    }

    @Test
    @DisplayName("POST /evaluate com JSON malformado retorna 400")
    void shouldRejectMalformedJsonBody() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("POST /evaluate com x-origin inválido retorna 400")
    void shouldRejectInvalidOriginHeader() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "INVALID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 92,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ORIGIN"));
    }

    @Test
    @DisplayName("POST /evaluate propaga erro inesperado como 500")
    void shouldReturnInternalErrorWhenServiceFails() throws Exception {
        when(violationService.evaluate(any())).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 92,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("POST /evaluate delega para o service e retorna 200")
    void shouldEvaluateViolation() throws Exception {
        when(violationService.evaluate(any())).thenReturn(new EvaluateViolationResponse(
                "ABC1D23",
                "RAD-CWB-001",
                92,
                85,
                60,
                new BigDecimal("41.67"),
                true,
                new ViolationResponse(ViolationSeverity.SERIOUS, "218-II"),
                FIXED_INSTANT
        ));

        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 92,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("ABC1D23"))
                .andExpect(jsonPath("$.consideredSpeed").value(85))
                .andExpect(jsonPath("$.hasViolation").value(true))
                .andExpect(jsonPath("$.violation.severity").value("SERIOUS"))
                .andExpect(jsonPath("$.violation.ctbCode").value("218-II"));

        verify(violationService).evaluate(any());
    }

    @Test
    @DisplayName("POST /evaluate sem x-origin retorna 400")
    void shouldRejectMissingOriginHeader() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 92,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ORIGIN"));
    }

    @Test
    @DisplayName("POST /evaluate com placa inválida retorna 400")
    void shouldRejectInvalidLicensePlate() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "INVALID",
                                  "measuredSpeed": 92,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_LICENSE_PLATE"))
                .andExpect(jsonPath("$.timestamp").value(FIXED_INSTANT.toString()));
    }

    @Test
    @DisplayName("GET /violations retorna lista por placa")
    void shouldFindViolationsByLicensePlate() throws Exception {
        when(violationService.findByLicensePlate("ABC1D23")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/violations").param("licensePlate", "ABC1D23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(violationService).findByLicensePlate("ABC1D23");
    }

    @Test
    @DisplayName("POST /evaluate com captureTimestamp inválido retorna 400")
    void shouldRejectInvalidCaptureTimestamp() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 92,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "not-a-timestamp"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CAPTURE_TIMESTAMP"))
                .andExpect(jsonPath("$.message").value("Invalid capture timestamp format"));
    }

    @Test
    @DisplayName("GET /violations com placa inválida retorna 400")
    void shouldRejectInvalidLicensePlateOnQuery() throws Exception {
        mockMvc.perform(get("/api/v1/violations").param("licensePlate", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_LICENSE_PLATE"));
    }

    @Test
    @DisplayName("GET /violations sem licensePlate retorna 400")
    void shouldRejectMissingLicensePlateParameter() throws Exception {
        mockMvc.perform(get("/api/v1/violations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_LICENSE_PLATE"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: licensePlate"));
    }

    @Test
    @DisplayName("GET /evaluate retorna 405")
    void shouldRejectGetOnEvaluateEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/violations/evaluate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("POST /violations retorna 405")
    void shouldRejectPostOnListEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/violations")
                        .param("licensePlate", "ABC1D23"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("POST /evaluate sem Content-Type application/json retorna 415")
    void shouldRejectUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("licensePlate=ABC1D23"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
