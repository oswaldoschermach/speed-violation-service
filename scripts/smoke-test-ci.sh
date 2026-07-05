#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="${BASE_URL}/api/v1/violations"

echo "==> Aguardando API em ${BASE_URL}..."
for i in $(seq 1 60); do
  if curl -sf "${BASE_URL}/actuator/health/readiness" >/dev/null 2>&1; then
    echo "API pronta."
    break
  fi
  if [[ "$i" -eq 60 ]]; then
    echo "Timeout: API não ficou pronta." >&2
    exit 1
  fi
  sleep 2
done

echo "==> Health check"
curl -sf "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'

echo "==> OpenAPI disponível"
curl -sf "${BASE_URL}/api-docs" | grep -q 'openapi'

TIMESTAMP="2026-06-08T14:30:00Z"
BODY=$(cat <<EOF
{
  "licensePlate": "SMK1E23",
  "measuredSpeed": 92,
  "speedLimit": 60,
  "equipmentId": "RAD-CI-001",
  "captureTimestamp": "${TIMESTAMP}"
}
EOF
)

echo "==> POST /evaluate (infração)"
RESP=$(curl -s -w "\n%{http_code}" -X POST "${API}/evaluate" \
  -H "Content-Type: application/json" \
  -H "x-origin: FIXED" \
  -d "${BODY}")
STATUS=$(echo "$RESP" | tail -n1)
BODY_OUT=$(echo "$RESP" | sed '$d')
[[ "$STATUS" == "200" ]] || { echo "Esperado 200, recebeu ${STATUS}: ${BODY_OUT}" >&2; exit 1; }
echo "$BODY_OUT" | grep -q '"hasViolation":true'

echo "==> GET /violations?licensePlate=SMK1E23"
curl -sf "${API}?licensePlate=SMK1E23" | grep -q 'SMK1E23'

echo "==> Validação 400 (placa inválida)"
INVALID_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${API}/evaluate" \
  -H "Content-Type: application/json" \
  -H "x-origin: FIXED" \
  -d '{"licensePlate":"INVALID","measuredSpeed":92,"speedLimit":60,"equipmentId":"RAD-CI-001","captureTimestamp":"2026-06-08T14:31:00Z"}')
[[ "$INVALID_STATUS" == "400" ]] || { echo "Esperado 400, recebeu ${INVALID_STATUS}" >&2; exit 1; }

echo "==> Smoke test CI OK"
