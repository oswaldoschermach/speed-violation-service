package com.velsis.speed_violation_service.persistence.entity;

import com.velsis.speed_violation_service.domain.model.CaptureOrigin;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import com.velsis.speed_violation_service.support.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag(TestTags.UNIT)
class ViolationEntityTest {

    private static final Instant CAPTURE = Instant.parse("2026-06-08T14:30:00Z");
    private static final Instant PROCESSED = Instant.parse("2026-06-08T14:30:05Z");

    @Test
    @DisplayName("equals compara por id quando ambos persistidos")
    void shouldCompareByIdWhenPersisted() throws Exception {
        UUID id = UUID.randomUUID();
        Violation first = sampleViolation();
        Violation second = sampleViolation();
        setId(first, id);
        setId(second, id);

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo(first);
        assertThat(first).isNotEqualTo("other");
        assertThat(first).isNotEqualTo(sampleViolation());
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    private static Violation sampleViolation() {
        return Violation.builder()
                .licensePlate("ABC1D23")
                .equipmentId("RAD-CWB-001")
                .measuredSpeed(92)
                .consideredSpeed(85)
                .speedLimit(60)
                .excessPercentage(new BigDecimal("41.67"))
                .severity(ViolationSeverity.SERIOUS)
                .ctbCode("218-II")
                .captureTimestamp(CAPTURE)
                .processedAt(PROCESSED)
                .origin(CaptureOrigin.FIXED)
                .build();
    }

    private static void setId(Violation violation, UUID id) throws Exception {
        Field idField = Violation.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(violation, id);
    }
}
