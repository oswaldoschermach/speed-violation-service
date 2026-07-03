package com.velsis.speed_violation_service.persistence;

import com.velsis.speed_violation_service.persistence.entity.Violation;
import com.velsis.speed_violation_service.persistence.repository.ViolationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ViolationPersistenceService {

    private final ViolationRepository violationRepository;

    public ViolationPersistenceService(ViolationRepository violationRepository) {
        this.violationRepository = violationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(Violation violation) {
        violationRepository.saveAndFlush(violation);
    }
}
