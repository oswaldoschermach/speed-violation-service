package com.velsis.speed_violation_service.domain.service;

import com.velsis.speed_violation_service.domain.exception.DuplicateViolationException;
import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.model.EvaluateViolationCommand;
import com.velsis.speed_violation_service.persistence.repository.ViolationRepository;
import com.velsis.speed_violation_service.support.TestTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag(TestTags.INTEGRATION)
@SpringBootTest
@Testcontainers
class ViolationServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ViolationService violationService;

    @Autowired
    private ViolationRepository violationRepository;

    @BeforeEach
    void setUp() {
        violationRepository.deleteAll();
    }

    @Test
    @DisplayName("requisições concorrentes com mesma captura persistem apenas uma infração")
    void shouldPersistOnlyOnceUnderConcurrentDuplicateEvaluations() throws Exception {
        EvaluateViolationCommand command = new EvaluateViolationCommand(
                "ABC1D23",
                92,
                60,
                "RAD-CWB-001",
                Instant.parse("2026-06-08T14:30:00Z"),
                CaptureOrigin.FIXED
        );

        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    violationService.evaluate(command);
                    successCount.incrementAndGet();
                } catch (DuplicateViolationException exception) {
                    duplicateCount.incrementAndGet();
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

        assertThat(violationRepository.count()).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(threads - 1);
    }
}
