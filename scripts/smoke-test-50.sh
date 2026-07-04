#!/usr/bin/env bash
# Smoke test completo: 50 casos contra a API em execução local.
# Uso: ./scripts/smoke-test-50.sh
# Pré-requisitos: stack local em docker/local, curl, jq
#   docker compose -f docker/local/compose.yaml up -d --build


#aqui eu criei usando o chatgpt para testar a api localmente, com base no swagger.
#muito util para testar a api localmente, mas nao para testar a api em producao.

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="${BASE_URL}/api/v1/violations"
PASS=0
FAIL=0
CASE=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Erro: '$1' não encontrado. Instale antes de rodar." >&2
    exit 1
  }
}

require_cmd curl
require_cmd jq

wait_for_api() {
  local retries="${1:-60}"
  for ((i = 1; i <= retries; i++)); do
    if curl -sf "${BASE_URL}/api-docs" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "API não respondeu em ${BASE_URL} após ${retries} tentativas." >&2
  return 1
}

truncate_db() {
  if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'speed-violation-postgres'; then
    docker exec speed-violation-postgres \
      psql -U speed_violation -d speed_violation -c "TRUNCATE violations;" >/dev/null
    echo -e "${YELLOW}Banco truncado (violations).${NC}"
  else
    echo -e "${YELLOW}Aviso: não foi possível truncar o banco — dados anteriores podem causar 409 no caso #1.${NC}"
  fi
}

# ts_offset: segundos únicos por caso para evitar colisão acidental
ts_for_case() {
  printf '2026-06-08T14:%02d:%02dZ' $((30 + CASE / 60)) $((CASE % 60))
}

assert_case() {
  local desc="$1"
  local expected_status="$2"
  local body_check="${3:-}"

  CASE=$((CASE + 1))
  local label
  label=$(printf '%02d' "$CASE")

  local status
  status=$(echo "$RESP" | tail -n1)
  local body
  body=$(echo "$RESP" | sed '$d')

  local ok=true
  local reason=""

  if [[ "$status" != "$expected_status" ]]; then
    ok=false
    reason="HTTP ${status} (esperado ${expected_status})"
  elif [[ -n "$body_check" ]] && ! echo "$body" | jq -e "$body_check" >/dev/null 2>&1; then
    ok=false
    reason="body não bate: $(echo "$body" | jq -c '.' 2>/dev/null || echo "$body")"
  fi

  if $ok; then
    PASS=$((PASS + 1))
    echo -e "${GREEN}✓${NC} #${label} ${desc} → HTTP ${status}"
  else
    FAIL=$((FAIL + 1))
    echo -e "${RED}✗${NC} #${label} ${desc} → ${reason}"
    echo "   body: $(echo "$body" | jq -c '.' 2>/dev/null || echo "$body")"
  fi
}

post_evaluate() {
  local origin="$1"
  local json="$2"
  RESP=$(curl -s -w "\n%{http_code}" -X POST "${API}/evaluate" \
    -H "Content-Type: application/json" \
    -H "x-origin: ${origin}" \
    -d "$json")
}

post_evaluate_no_origin() {
  local json="$1"
  RESP=$(curl -s -w "\n%{http_code}" -X POST "${API}/evaluate" \
    -H "Content-Type: application/json" \
    -d "$json")
}

post_evaluate_raw() {
  # args: headers... -- body
  local headers=()
  while [[ $# -gt 0 && "$1" != "--" ]]; do
    headers+=("$1")
    shift
  done
  shift
  local body="$1"
  RESP=$(curl -s -w "\n%{http_code}" -X POST "${API}/evaluate" "${headers[@]}" -d "$body")
}

get_violations() {
  local plate="$1"
  RESP=$(curl -s -w "\n%{http_code}" -G "${API}" --data-urlencode "licensePlate=${plate}")
}

get_violations_no_param() {
  RESP=$(curl -s -w "\n%{http_code}" "${API}")
}

raw_request() {
  local method="$1"
  local url="$2"
  shift 2
  RESP=$(curl -s -w "\n%{http_code}" -X "$method" "$url" "$@")
}

main() {
  echo "=== Speed Violation Service — smoke test (50 casos) ==="
  echo "Base URL: ${BASE_URL}"
  echo

  if ! wait_for_api 3; then
    echo "Aguardando API subir..."
    wait_for_api 90 || exit 1
  fi

  truncate_db
  echo

  local ts

  # --- POST /evaluate — sucesso (1-15) ---

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":92,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Infração SERIOUS (exemplo da prova)" 200 '.hasViolation == true and .violation.severity == "SERIOUS" and .consideredSpeed == 85'

  ts=$(ts_for_case)
  post_evaluate MOBILE "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":64,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-002\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Sem infração (dentro da tolerância −7 km/h)" 200 '.hasViolation == false and .violation == null and .consideredSpeed == 57'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1234\",\"measuredSpeed\":72,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-003\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Placa formato antigo com infração" 200 '.hasViolation == true and .licensePlate == "ABC1234"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"QWE9A87\",\"measuredSpeed\":120,\"speedLimit\":110,\"equipmentId\":\"RAD-PRV-010\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Via >100 km/h — tolerância percentual truncada" 200 '.hasViolation == true and .consideredSpeed == 111'

  ts=$(ts_for_case)
  post_evaluate HANDHELD "{\"licensePlate\":\"BRA2E34\",\"measuredSpeed\":79,\"speedLimit\":60,\"equipmentId\":\"RAD-HND-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Gravidade MEDIUM (excesso ~20%)" 200 '.hasViolation == true and .violation.severity == "MEDIUM" and .violation.ctbCode == "218-I"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"BRA2E34\",\"measuredSpeed\":98,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-004\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Gravidade VERY_SERIOUS (excesso >50%)" 200 '.hasViolation == true and .violation.severity == "VERY_SERIOUS" and .violation.ctbCode == "218-III"'

  ts=$(ts_for_case)
  post_evaluate MOBILE "{\"licensePlate\":\"XYZ9Z99\",\"measuredSpeed\":67,\"speedLimit\":60,\"equipmentId\":\"RAD-MOB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Limite exato após tolerância — sem infração (67→60)" 200 '.hasViolation == false and .consideredSpeed == 60'

  ts=$(ts_for_case)
  post_evaluate MOBILE "{\"licensePlate\":\"XYZ9Z99\",\"measuredSpeed\":68,\"speedLimit\":60,\"equipmentId\":\"RAD-MOB-002\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Um km/h acima do limite tolerado — com infração (68→61)" 200 '.hasViolation == true and .consideredSpeed == 61'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"JKL3M45\",\"measuredSpeed\":107,\"speedLimit\":100,\"equipmentId\":\"RAD-100-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Limite 100 km/h — considered igual ao limite, sem infração" 200 '.hasViolation == false and .consideredSpeed == 100'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"JKL3M45\",\"measuredSpeed\":108,\"speedLimit\":100,\"equipmentId\":\"RAD-100-002\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Limite 100 km/h — 1 km/h acima do limite tolerado" 200 '.hasViolation == true and .consideredSpeed == 101'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"MNO4P56\",\"measuredSpeed\":115,\"speedLimit\":101,\"equipmentId\":\"RAD-101-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Limite 101 km/h — margem percentual (115→106)" 200 '.hasViolation == true and .consideredSpeed == 106'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"MNO4P56\",\"measuredSpeed\":84,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-005\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Gravidade SERIOUS (~28% excesso, 84→77)" 200 '.hasViolation == true and .violation.severity == "SERIOUS"'

  ts=$(ts_for_case)
  post_evaluate MOBILE "{\"licensePlate\":\"PQR5S67\",\"measuredSpeed\":80,\"speedLimit\":60,\"equipmentId\":\"RAD-MOB-003\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Mesma placa, equipamento diferente — persiste" 200 '.hasViolation == true'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"PQR5S67\",\"measuredSpeed\":85,\"speedLimit\":60,\"equipmentId\":\"RAD-MOB-003\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Mesma placa+equipamento, timestamp diferente — persiste" 200 '.hasViolation == true'

  ts=$(ts_for_case)
  post_evaluate HANDHELD "{\"licensePlate\":\"STU6V78\",\"measuredSpeed\":1,\"speedLimit\":1,\"equipmentId\":\"RAD-MIN-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Velocidades mínimas válidas (1/1) — sem infração após tolerância" 200 '.hasViolation == false'

  # --- POST /evaluate — erros (16-30) ---

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"INVALID\",\"measuredSpeed\":92,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Placa inválida → 400 INVALID_LICENSE_PLATE" 400 '.error == "INVALID_LICENSE_PLATE"'

  ts=$(ts_for_case)
  post_evaluate_no_origin "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":92,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Header x-origin ausente → 400 INVALID_ORIGIN" 400 '.error == "INVALID_ORIGIN"'

  ts=$(ts_for_case)
  post_evaluate BOGUS "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":92,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Header x-origin inválido → 400" 400 '.error == "INVALID_ORIGIN"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":0,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Velocidade medida zero → 400" 400 '.error == "INVALID_MEASURED_SPEED"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":-5,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Velocidade medida negativa → 400" 400 '.error == "INVALID_MEASURED_SPEED"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":80,\"speedLimit\":0,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Limite de velocidade zero → 400" 400 '.error == "INVALID_SPEED_LIMIT"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"\",\"measuredSpeed\":80,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Placa em branco → 400" 400 '.error == "INVALID_LICENSE_PLATE"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":80,\"speedLimit\":60,\"equipmentId\":\"\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "EquipmentId em branco → 400" 400 '.error == "INVALID_EQUIPMENT_ID"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":80,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"2030-01-01T00:00:00Z\"}"
  assert_case "Timestamp no futuro → 400" 400 '.error == "INVALID_CAPTURE_TIMESTAMP"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":80,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"not-a-date\"}"
  assert_case "Timestamp malformado → 400" 400 '.error == "INVALID_CAPTURE_TIMESTAMP"'

  # Duplicata: reutiliza corpo do caso #1 (ABC1D23, RAD-CWB-001, mesmo ts do primeiro caso)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":92,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"2026-06-08T14:30:00Z\"}"
  assert_case "Captura duplicada → 409 DUPLICATE_VIOLATION" 409 '.error == "DUPLICATE_VIOLATION"'

  ts=$(ts_for_case)
  post_evaluate_raw -H "Content-Type: text/plain" -H "x-origin: FIXED" -- "not json"
  assert_case "Content-Type text/plain → 415" 415 '.error == "UNSUPPORTED_MEDIA_TYPE"'

  ts=$(ts_for_case)
  post_evaluate_raw -H "Content-Type: application/json" -H "x-origin: FIXED" -- "{broken json"
  assert_case "JSON malformado → 400" 400 '.error == "INVALID_REQUEST"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"abc1d23\",\"measuredSpeed\":80,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Placa minúscula (regex exige maiúsculas) → 400" 400 '.error == "INVALID_LICENSE_PLATE"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":null,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "measuredSpeed null → 400" 400 '.error == "INVALID_MEASURED_SPEED"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"ABC1D23\",\"measuredSpeed\":80,\"speedLimit\":null,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "speedLimit null → 400" 400 '.error == "INVALID_SPEED_LIMIT"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"measuredSpeed\":80,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-001\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Campo licensePlate ausente → 400" 400 '.error == "INVALID_LICENSE_PLATE"'

  # --- GET /violations (33-42) ---

  get_violations ABC1D23
  assert_case "GET placa com registros (ABC1D23)" 200 '. | length >= 1'

  get_violations ABC1234
  assert_case "GET placa formato antigo com registro" 200 '.[0].licensePlate == "ABC1234"'

  get_violations BRA2E34
  assert_case "GET placa com múltiplos registros" 200 '. | length >= 2'

  get_violations ZZZ9Z99
  assert_case "GET placa sem registros → lista vazia" 200 '. | length == 0'

  get_violations INVALID
  assert_case "GET placa inválida → 400" 400 '.error == "INVALID_LICENSE_PLATE"'

  get_violations_no_param
  assert_case "GET sem query param licensePlate → 400" 400 '.error == "INVALID_LICENSE_PLATE"'

  get_violations QWE9A87
  assert_case "GET infração highway persistida — origin FIXED" 200 '.[0].origin == "FIXED" and .[0].severity != null'

  get_violations PQR5S67
  assert_case "GET confirma dois registros (equip/timestamp distintos)" 200 '. | length == 2'

  get_violations STU6V78
  assert_case "GET placa sem infração persistida → vazio" 200 '. | length == 0'

  get_violations "ABC%20123"
  assert_case "GET placa com caracteres inválidos → 400" 400 '.error == "INVALID_LICENSE_PLATE"'

  # --- Métodos HTTP e borda (43-50) ---

  raw_request GET "${API}/evaluate"
  assert_case "GET em /evaluate → 405 METHOD_NOT_ALLOWED" 405 '.error == "METHOD_NOT_ALLOWED"'

  raw_request POST "${API}/evaluate" -H "Content-Type: application/json" -H "x-origin: FIXED"
  assert_case "POST /evaluate sem body → 400" 400

  raw_request POST "${API}" -H "Content-Type: application/json" -H "x-origin: FIXED" \
    -d '{"licensePlate":"ABC1D23","measuredSpeed":80,"speedLimit":60,"equipmentId":"X","captureTimestamp":"2026-06-08T14:30:00Z"}'
  assert_case "POST em /violations (rota errada) → 405" 405 '.error == "METHOD_NOT_ALLOWED"'

  ts=$(ts_for_case)
  post_evaluate FIXED "{\"licensePlate\":\"VWX7Y89\",\"measuredSpeed\":73,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-006\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Excesso pequeno — MEDIUM (73→66, 10% excesso)" 200 '.violation.severity == "MEDIUM"'

  ts=$(ts_for_case)
  post_evaluate MOBILE "{\"licensePlate\":\"VWX7Y89\",\"measuredSpeed\":88,\"speedLimit\":60,\"equipmentId\":\"RAD-CWB-007\",\"captureTimestamp\":\"${ts}\"}"
  assert_case "Excesso 35% — SERIOUS" 200 '.violation.severity == "SERIOUS"'

  CASE=$((CASE + 1))
  if curl -sf "${BASE_URL}/api-docs" | jq -e '.info.title == "Speed Violation Service API"' >/dev/null; then
    PASS=$((PASS + 1))
    echo -e "${GREEN}✓${NC} #$(printf '%02d' "$CASE") OpenAPI /api-docs acessível com título correto → HTTP 200"
  else
    FAIL=$((FAIL + 1))
    echo -e "${RED}✗${NC} #$(printf '%02d' "$CASE") OpenAPI /api-docs"
  fi

  CASE=$((CASE + 1))
  if curl -sf "${BASE_URL}/swagger-ui.html" >/dev/null; then
    PASS=$((PASS + 1))
    echo -e "${GREEN}✓${NC} #$(printf '%02d' "$CASE") Swagger UI acessível → HTTP 200"
  else
    FAIL=$((FAIL + 1))
    echo -e "${RED}✗${NC} #$(printf '%02d' "$CASE") Swagger UI"
  fi

  get_violations VWX7Y89
  assert_case "GET final — placa VWX7Y89 com 2 infrações persistidas" 200 '. | length == 2'

  echo
  echo "========================================"
  echo -e "Resultado: ${GREEN}${PASS} passou${NC}, ${RED}${FAIL} falhou${NC} (total ${CASE})"
  echo "========================================"

  [[ "$FAIL" -eq 0 ]]
}

main "$@"
