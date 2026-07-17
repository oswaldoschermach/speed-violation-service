package com.nebulax.speed_violation_service.config;

import com.nebulax.speed_violation_service.domain.service.ViolationEvaluationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DomainServiceConfig {

    @Bean
    ViolationEvaluationService violationEvaluationService(ToleranceProperties tolerance) {
        return new ViolationEvaluationService(tolerance);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
