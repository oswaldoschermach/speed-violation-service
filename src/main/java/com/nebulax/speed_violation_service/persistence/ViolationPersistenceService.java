package com.nebulax.speed_violation_service.persistence;

import com.nebulax.speed_violation_service.persistence.entity.Violation;
import com.nebulax.speed_violation_service.persistence.repository.ViolationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ViolationPersistenceService {

    private final ViolationRepository violationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(Violation violation) {
        violationRepository.saveAndFlush(violation);
    }
}
