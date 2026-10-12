#!/usr/bin/env bash
# Starts PostgreSQL and Keycloak and waits until both are ready.
# Usage: scripts/env-up.sh
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
REALM="${REALM:-partnerguard}"
POSTGRES_USER="${POSTGRES_USER:-partnerguard}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-180}"

cd "$(dirname "$0")/.."

docker compose up -d

deadline=$((SECONDS + TIMEOUT_SECONDS))

wait_for() {
  local name="$1"; shift
  printf 'Waiting for %s' "$name"
  until "$@" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      printf '\nerror: %s not ready after %ss\n' "$name" "$TIMEOUT_SECONDS" >&2
      exit 1
    fi
    printf '.'
    sleep 2
  done
  printf ' ready\n'
}

wait_for "PostgreSQL" docker compose exec -T postgres pg_isready -U "$POSTGRES_USER"
# Keycloak joins docker-compose.yml in milestone 3.
if docker compose config --services | grep -qx keycloak; then
  wait_for "Keycloak realm '$REALM'" curl -sf "$KEYCLOAK_URL/realms/$REALM/.well-known/openid-configuration"
fi

echo "Environment is up. Start the API with: ./mvnw spring-boot:run"
