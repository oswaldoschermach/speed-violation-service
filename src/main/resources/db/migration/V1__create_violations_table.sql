CREATE TABLE violations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    license_plate     VARCHAR(7)     NOT NULL,
    equipment_id      VARCHAR(50)    NOT NULL,
    measured_speed    INTEGER        NOT NULL,
    considered_speed  INTEGER        NOT NULL,
    speed_limit       INTEGER        NOT NULL,
    excess_percentage NUMERIC(6, 2)  NOT NULL,
    severity          VARCHAR(20)    NOT NULL,
    ctb_code          VARCHAR(10)    NOT NULL,
    capture_timestamp TIMESTAMPTZ    NOT NULL,
    processed_at      TIMESTAMPTZ    NOT NULL,
    origin            VARCHAR(20)    NOT NULL
);

CREATE INDEX idx_violations_license_plate ON violations (license_plate);
CREATE INDEX idx_violations_license_plate_processed_at ON violations (license_plate, processed_at DESC);
