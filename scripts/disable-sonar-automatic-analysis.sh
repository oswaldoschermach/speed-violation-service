#!/usr/bin/env bash
set -euo pipefail

PROJECT_KEY="${SONAR_PROJECT_KEY:-oswaldoschermach_speed-violation-service}"

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "Defina SONAR_TOKEN (token de usuário em SonarCloud → My Account → Security)." >&2
  exit 1
fi

HTTP=$(curl -s -o /tmp/sonar-autoscan.json -w '%{http_code}' -X POST \
  'https://sonarcloud.io/api/autoscan/activation' \
  -u "${SONAR_TOKEN}:" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'enable=false' \
  --data-urlencode "projectKey=${PROJECT_KEY}")

if [[ "$HTTP" == "204" || "$HTTP" == "200" ]]; then
  echo "Automatic Analysis desativada para ${PROJECT_KEY}."
  exit 0
fi

cat /tmp/sonar-autoscan.json
echo "Falha HTTP ${HTTP}. Desative manualmente: SonarCloud → Administration → Analysis Method." >&2
exit 1
