package com.velsis.speed_violation_service.api;

import com.velsis.speed_violation_service.persistence.repository.ViolationRepository;
import com.velsis.speed_violation_service.support.TestTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag(TestTags.INTEGRATION)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ViolationApiConcurrencyIntegrationTest {

    private static final String EVALUATE_BODY = """
            {
              "licensePlate": "ABC1D23",
              "measuredSpeed": 92,
              "speedLimit": 60,
              "equipmentId": "RAD-CWB-001",
              "captureTimestamp": "2026-06-08T14:30:00Z"
            }
            """;

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
    @DisplayName("HTTP concorrente: mesma captura retorna 1x 200 e Nx 409 com um único registro")
    void shouldPersistOnlyOnceUnderConcurrentHttpEvaluations() throws Exception {
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/violations/evaluate")
                                    .header("x-origin", "FIXED")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(EVALUATE_BODY))
                            .andReturn();

                    switch (result.getResponse().getStatus()) {
                        case 200 -> successCount.incrementAndGet();
                        case 409 -> conflictCount.incrementAndGet();
                        default -> unexpectedCount.incrementAndGet();
                    }
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(unexpectedCount.get()).isZero();
        assertThat(violationRepository.count()).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threads - 1);
    }
}
