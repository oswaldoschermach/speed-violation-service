package com.velsis.speed_violation_service.api;

import com.velsis.speed_violation_service.persistence.repository.ViolationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ViolationApiValidationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ViolationRepository violationRepository;

    @BeforeEach
    void setUp() {
        violationRepository.deleteAll();
    }

    @Test
    @DisplayName("HTTP 400: placa inválida no POST /evaluate")
    void shouldRejectInvalidLicensePlateOverHttp() throws Exception {
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
                .andExpect(jsonPath("$.message").value("Invalid license plate format"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(violationRepository.count()).isZero();
    }

    @Test
    @DisplayName("HTTP 400: header x-origin ausente no POST /evaluate")
    void shouldRejectMissingOriginHeaderOverHttp() throws Exception {
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
                .andExpect(jsonPath("$.error").value("INVALID_ORIGIN"))
                .andExpect(jsonPath("$.message").value("Missing x-origin header"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(violationRepository.count()).isZero();
    }

    @Test
    @DisplayName("HTTP 400: captureTimestamp com formato inválido no POST /evaluate")
    void shouldRejectMalformedCaptureTimestampOverHttp() throws Exception {
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
                .andExpect(jsonPath("$.message").value("Invalid capture timestamp format"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(violationRepository.count()).isZero();
    }

    @Test
    @DisplayName("HTTP 400: captureTimestamp no futuro no POST /evaluate")
    void shouldRejectFutureCaptureTimestampOverHttp() throws Exception {
        Instant future = Instant.now().plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 92,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "%s"
                                }
                                """.formatted(future)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CAPTURE_TIMESTAMP"))
                .andExpect(jsonPath("$.message").value("Capture timestamp cannot be in the future"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(violationRepository.count()).isZero();
    }
}
