ALTER TABLE violations
    ADD CONSTRAINT uq_violations_capture
        UNIQUE (license_plate, equipment_id, capture_timestamp);
