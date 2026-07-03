package com.velsis.speed_violation_service.domain.service;

import com.velsis.speed_violation_service.config.ToleranceProperties;
import com.velsis.speed_violation_service.domain.model.ViolationSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ViolationEvaluationServiceTest {

    private ViolationEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new ViolationEvaluationService(new ToleranceProperties(7, 7, 100));
    }

    @Test
    @DisplayName("exemplo da prova: infração grave com 41,67% de excesso")
    void shouldMatchPdfViolationExample() {
        int consideredSpeed = service.calculateConsideredSpeed(92, 60);
        BigDecimal excessPercentage = service.calculateExcessPercentage(consideredSpeed, 60);

        assertThat(consideredSpeed).isEqualTo(85);
        assertThat(excessPercentage).isEqualByComparingTo(new BigDecimal("41.67"));
        assertThat(service.hasViolation(consideredSpeed, 60)).isTrue();
        assertThat(service.determineSeverity(excessPercentage)).isEqualTo(ViolationSeverity.SERIOUS);
    }

    @Test
    @DisplayName("exemplo da prova: sem infração dentro da tolerância")
    void shouldMatchPdfNoViolationExample() {
        int consideredSpeed = service.calculateConsideredSpeed(64, 60);
        BigDecimal excessPercentage = service.calculateExcessPercentage(consideredSpeed, 60);

        assertThat(consideredSpeed).isEqualTo(57);
        assertThat(excessPercentage).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(service.hasViolation(consideredSpeed, 60)).isFalse();
    }

    @Test
    @DisplayName("velocidade medida igual ao limite não gera infração")
    void shouldNotViolateWhenMeasuredSpeedEqualsLimit() {
        int consideredSpeed = service.calculateConsideredSpeed(60, 60);

        assertThat(consideredSpeed).isEqualTo(53);
        assertThat(service.hasViolation(consideredSpeed, 60)).isFalse();
    }

    @Test
    @DisplayName("velocidade considerada igual ao limite não gera infração")
    void shouldNotViolateWhenConsideredSpeedEqualsLimit() {
        int consideredSpeed = service.calculateConsideredSpeed(67, 60);
        BigDecimal excessPercentage = service.calculateExcessPercentage(consideredSpeed, 60);

        assertThat(consideredSpeed).isEqualTo(60);
        assertThat(excessPercentage).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(service.hasViolation(consideredSpeed, 60)).isFalse();
    }

    @Test
    @DisplayName("limite acima de 100 km/h trunca a velocidade considerada (leitura literal do PDF)")
    void shouldApplyPercentToleranceWhenLimitAboveThreshold() {
        int consideredSpeed = service.calculateConsideredSpeed(120, 110);

        assertThat(consideredSpeed).isEqualTo(111);
        assertThat(service.hasViolation(consideredSpeed, 110)).isTrue();
    }

    @Test
    @DisplayName("tolerância percentual trunca (não arredonda) a velocidade considerada")
    void shouldTruncateConsideredSpeedUnderPercentTolerance() {
        assertThat(service.calculateConsideredSpeed(100, 110)).isEqualTo(93);
        assertThat(service.calculateConsideredSpeed(115, 110)).isEqualTo(106);
    }

    @Test
    @DisplayName("exatamente 20% de excesso classifica como média")
    void shouldClassifyExactlyTwentyPercentAsMedium() {
        int consideredSpeed = service.calculateConsideredSpeed(127, 100);
        BigDecimal excessPercentage = service.calculateExcessPercentage(consideredSpeed, 100);

        assertThat(consideredSpeed).isEqualTo(120);
        assertThat(excessPercentage).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(service.determineSeverity(excessPercentage)).isEqualTo(ViolationSeverity.MEDIUM);
    }

    @Test
    @DisplayName("acima de 20% de excesso classifica como grave")
    void shouldClassifyAboveTwentyPercentAsSerious() {
        int consideredSpeed = service.calculateConsideredSpeed(128, 100);
        BigDecimal excessPercentage = service.calculateExcessPercentage(consideredSpeed, 100);

        assertThat(consideredSpeed).isEqualTo(121);
        assertThat(excessPercentage).isEqualByComparingTo(new BigDecimal("21.00"));
        assertThat(service.determineSeverity(excessPercentage)).isEqualTo(ViolationSeverity.SERIOUS);
    }

    @Test
    @DisplayName("exatamente 50% de excesso classifica como grave")
    void shouldClassifyExactlyFiftyPercentAsSerious() {
        int consideredSpeed = service.calculateConsideredSpeed(157, 100);
        BigDecimal excessPercentage = service.calculateExcessPercentage(consideredSpeed, 100);

        assertThat(consideredSpeed).isEqualTo(150);
        assertThat(excessPercentage).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(service.determineSeverity(excessPercentage)).isEqualTo(ViolationSeverity.SERIOUS);
    }

    @Test
    @DisplayName("acima de 50% de excesso classifica como gravíssima")
    void shouldClassifyAboveFiftyPercentAsVerySerious() {
        int consideredSpeed = service.calculateConsideredSpeed(158, 100);
        BigDecimal excessPercentage = service.calculateExcessPercentage(consideredSpeed, 100);

        assertThat(consideredSpeed).isEqualTo(151);
        assertThat(excessPercentage).isEqualByComparingTo(new BigDecimal("51.00"));
        assertThat(service.determineSeverity(excessPercentage)).isEqualTo(ViolationSeverity.VERY_SERIOUS);
    }
}
