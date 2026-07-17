package com.nebulax.speed_violation_service.api;

import com.nebulax.speed_violation_service.persistence.repository.ViolationRepository;
import com.nebulax.speed_violation_service.support.TestTags;
import org.junit.jupiter.api.Tag;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag(TestTags.INTEGRATION)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ViolationApiIntegrationTest {

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
    @DisplayName("fluxo completo: apura infração, persiste e recupera pela placa")
    void shouldEvaluatePersistAndQueryViolation() throws Exception {
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
                .andExpect(jsonPath("$.hasViolation").value(true))
                .andExpect(jsonPath("$.consideredSpeed").value(85))
                .andExpect(jsonPath("$.excessPercentage").value(41.67))
                .andExpect(jsonPath("$.violation.severity").value("SERIOUS"))
                .andExpect(jsonPath("$.violation.ctbCode").value("218-II"));

        assertThat(violationRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/violations").param("licensePlate", "ABC1D23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].licensePlate").value("ABC1D23"))
                .andExpect(jsonPath("$[0].severity").value("SERIOUS"))
                .andExpect(jsonPath("$[0].origin").value("FIXED"));
    }

    @Test
    @DisplayName("fluxo completo: sem infração não persiste e consulta retorna vazio")
    void shouldNotPersistWhenNoViolation() throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "MOBILE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licensePlate": "ABC1D23",
                                  "measuredSpeed": 64,
                                  "speedLimit": 60,
                                  "equipmentId": "RAD-CWB-001",
                                  "captureTimestamp": "2026-06-08T14:30:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasViolation").value(false))
                .andExpect(jsonPath("$.violation").value(org.hamcrest.Matchers.nullValue()));

        assertThat(violationRepository.count()).isZero();

        mockMvc.perform(get("/api/v1/violations").param("licensePlate", "ABC1D23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("captura duplicada retorna 409 e mantém um único registro")
    void shouldRejectDuplicateCaptureWithConflict() throws Exception {
        String body = """
                {
                  "licensePlate": "ABC1D23",
                  "measuredSpeed": 92,
                  "speedLimit": 60,
                  "equipmentId": "RAD-CWB-001",
                  "captureTimestamp": "2026-06-08T14:30:00Z"
                }
                """;

        evaluateViolation(body);
        assertThat(violationRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Violation already registered for this capture"));

        assertThat(violationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("rota inexistente retorna 404")
    void shouldReturnNotFoundForUnknownRoute() throws Exception {
        mockMvc.perform(get("/api/v1/naoexiste"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private void evaluateViolation(String body) throws Exception {
        mockMvc.perform(post("/api/v1/violations/evaluate")
                        .header("x-origin", "FIXED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
