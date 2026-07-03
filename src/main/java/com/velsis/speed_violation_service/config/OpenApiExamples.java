package com.velsis.speed_violation_service.config;

/**
 * Exemplos reutilizados na documentação OpenAPI/Swagger (contrato da prova Velsis).
 */
public final class OpenApiExamples {

    private OpenApiExamples() {
    }

    public static final String EVALUATE_WITH_VIOLATION_REQUEST = """
            {
              "licensePlate": "ABC1D23",
              "measuredSpeed": 92,
              "speedLimit": 60,
              "equipmentId": "RAD-CWB-001",
              "captureTimestamp": "2026-06-08T14:30:00Z"
            }
            """;

    public static final String EVALUATE_NO_VIOLATION_REQUEST = """
            {
              "licensePlate": "ABC1D23",
              "measuredSpeed": 64,
              "speedLimit": 60,
              "equipmentId": "RAD-CWB-001",
              "captureTimestamp": "2026-06-08T14:30:00Z"
            }
            """;

    public static final String EVALUATE_LEGACY_PLATE_REQUEST = """
            {
              "licensePlate": "ABC1234",
              "measuredSpeed": 72,
              "speedLimit": 60,
              "equipmentId": "RAD-CWB-002",
              "captureTimestamp": "2026-06-08T14:30:00Z"
            }
            """;

    public static final String EVALUATE_HIGHWAY_REQUEST = """
            {
              "licensePlate": "QWE9A87",
              "measuredSpeed": 120,
              "speedLimit": 110,
              "equipmentId": "RAD-PRV-010",
              "captureTimestamp": "2026-06-08T14:30:00Z"
            }
            """;

    public static final String EVALUATE_INVALID_PLATE_REQUEST = """
            {
              "licensePlate": "INVALID",
              "measuredSpeed": 92,
              "speedLimit": 60,
              "equipmentId": "RAD-CWB-001",
              "captureTimestamp": "2026-06-08T14:30:00Z"
            }
            """;

    public static final String EVALUATE_WITH_VIOLATION_RESPONSE = """
            {
              "licensePlate": "ABC1D23",
              "equipmentId": "RAD-CWB-001",
              "measuredSpeed": 92,
              "consideredSpeed": 85,
              "speedLimit": 60,
              "excessPercentage": 41.67,
              "hasViolation": true,
              "violation": {
                "severity": "SERIOUS",
                "ctbCode": "218-II"
              },
              "processedAt": "2026-06-08T14:30:05Z"
            }
            """;

    public static final String EVALUATE_NO_VIOLATION_RESPONSE = """
            {
              "licensePlate": "ABC1D23",
              "equipmentId": "RAD-CWB-001",
              "measuredSpeed": 64,
              "consideredSpeed": 57,
              "speedLimit": 60,
              "excessPercentage": 0.0,
              "hasViolation": false,
              "violation": null,
              "processedAt": "2026-06-08T14:30:05Z"
            }
            """;

    public static final String STORED_VIOLATIONS_RESPONSE = """
            [
              {
                "licensePlate": "ABC1D23",
                "equipmentId": "RAD-CWB-001",
                "measuredSpeed": 92,
                "consideredSpeed": 85,
                "speedLimit": 60,
                "excessPercentage": 41.67,
                "severity": "SERIOUS",
                "ctbCode": "218-II",
                "captureTimestamp": "2026-06-08T14:30:00Z",
                "processedAt": "2026-06-08T14:30:05Z",
                "origin": "FIXED"
              }
            ]
            """;

    public static final String STORED_VIOLATIONS_EMPTY_RESPONSE = "[]";

    public static final String ERROR_INVALID_LICENSE_PLATE = """
            {
              "error": "INVALID_LICENSE_PLATE",
              "message": "Invalid license plate format",
              "timestamp": "2026-06-08T14:30:05Z"
            }
            """;

    public static final String ERROR_INVALID_ORIGIN = """
            {
              "error": "INVALID_ORIGIN",
              "message": "Missing x-origin header",
              "timestamp": "2026-06-08T14:30:05Z"
            }
            """;

    public static final String ERROR_INVALID_CAPTURE_TIMESTAMP = """
            {
              "error": "INVALID_CAPTURE_TIMESTAMP",
              "message": "Invalid capture timestamp format",
              "timestamp": "2026-06-08T14:30:05Z"
            }
            """;

    public static final String ERROR_INVALID_MEASURED_SPEED = """
            {
              "error": "INVALID_MEASURED_SPEED",
              "message": "Measured speed must be greater than zero",
              "timestamp": "2026-06-08T14:30:05Z"
            }
            """;

    public static final String ERROR_DUPLICATE_VIOLATION = """
            {
              "error": "DUPLICATE_VIOLATION",
              "message": "Violation already registered for this capture",
              "timestamp": "2026-06-08T14:30:05Z"
            }
            """;
}
