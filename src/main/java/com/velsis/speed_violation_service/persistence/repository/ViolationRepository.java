package com.velsis.speed_violation_service.persistence.repository;

import com.velsis.speed_violation_service.persistence.entity.Violation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ViolationRepository extends JpaRepository<Violation, UUID> {

    List<Violation> findByLicensePlateOrderByProcessedAtDesc(String licensePlate);

    boolean existsByLicensePlateAndEquipmentIdAndCaptureTimestamp(
            String licensePlate, String equipmentId, Instant captureTimestamp);
}
