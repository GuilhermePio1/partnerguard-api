#!/usr/bin/env bash
# Calls the running API with each fixture user and checks the status codes
# defined in specs/. Only sends requests that must fail before any write, so
# the seed data stays unchanged when the API is correct.
# Usage: scripts/smoke-test.sh
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Fixture ids (specs/fixtures.md)
PARTNER_B=10000000-0000-4000-8000-00000000000b
CONTRACT_A=30000000-0000-4000-8000-00000000000a
CONTRACT_B=30000000-0000-4000-8000-00000000000b
CLAIM_A1=40000000-0000-4000-8000-00000000a001
CLAIM_A2=40000000-0000-4000-8000-00000000a002
CLAIM_B1=40000000-0000-4000-8000-00000000b001
MISSING_ID=40000000-0000-4000-8000-000000000000

tmp="$(mktemp -d)"
# Git Bash on Windows: give native tools (curl.exe) a C:/... path.
if command -v cygpath >/dev/null 2>&1; then tmp="$(cygpath -m "$tmp")"; fi
trap 'rm -rf "$tmp"' EXIT
BODY="$tmp/body"
HEADERS="$tmp/headers"

passed=0
failed=0

# expect <criterion> <expected-status> <method> <path> <token> [json-body]
expect() {
  local id="$1" expected="$2" method="$3" path="$4" token="$5" body="${6:-}"
  local args=(-s -o "$BODY" -D "$HEADERS" -w '%{http_code}' -X "$method")
  if [ -n "$token" ]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' --data-binary "$body")
  fi

  local status
  status="$(curl "${args[@]}" "$API_URL$path" || true)"

  if [ "$status" = "$expected" ]; then
    passed=$((passed + 1))
    printf 'PASS  %-9s %-6s %-60s %s\n' "$id" "$method" "$path" "$status"
  else
    failed=$((failed + 1))
    printf 'FAIL  %-9s %-6s %-60s expected %s, got %s\n' "$id" "$method" "$path" "$expected" "$status"
  fi
}

# check <criterion> <description> <command...>  (command must succeed)
check() {
  local id="$1" description="$2"; shift 2
  if "$@"; then
    passed=$((passed + 1))
    printf 'PASS  %-9s %s\n' "$id" "$description"
  else
    failed=$((failed + 1))
    printf 'FAIL  %-9s %s\n' "$id" "$description"
  fi
}

# absent <extended-regex> <file>  (succeeds when the pattern is NOT in the file)
absent() {
  ! grep -Eq "$1" "$2"
}

echo "Getting tokens..."
admin="$("$SCRIPT_DIR/get-token.sh" admin)"
analyst="$("$SCRIPT_DIR/get-token.sh" analyst)"
shop_a="$("$SCRIPT_DIR/get-token.sh" shop-a)"

echo "Running checks against $API_URL"

# Authentication and public endpoints
expect AUTHN-01 401 GET  /api/v1/claims ""
expect AUTHZ-14 200 GET  /actuator/health ""

# Partners
expect AUTHZ-03 200 GET  /api/v1/partners "$analyst"
expect AUTHZ-03 403 GET  /api/v1/partners "$shop_a"
expect AUTHZ-03 403 POST /api/v1/partners "$analyst" '{"legalName":"Oficina Gama Ltda","taxId":"33.444.555/0001-81"}'
expect PRT-05   400 POST /api/v1/partners "$admin"   '{"legalName":"Oficina Gama Ltda","taxId":"12.ABC.345/01DE-36"}'
expect AUTHZ-03 403 GET  /api/v1/partners/me "$admin"
expect PRT-10   200 GET  /api/v1/partners/me "$shop_a"
check  HRD-12 "taxId is masked in /partners/me" grep -q '"\*\*\.\*\*\*\.\*\*\*/\*\*\*\*-81"' "$BODY"
check  HRD-12 "unmasked taxId is absent" absent '11222333000181|11\.222\.333/0001-81' "$BODY"
check  HRD-12 "X-Content-Type-Options: nosniff" grep -qi '^X-Content-Type-Options: *nosniff' "$HEADERS"

# Contracts
expect CTR-04   200 GET  "/api/v1/contracts/$CONTRACT_A" "$shop_a"
expect CTR-03   404 GET  "/api/v1/contracts/$CONTRACT_B" "$shop_a"

# Claims: reads
expect CLM-04   200 GET  "/api/v1/claims/$CLAIM_A1" "$shop_a"
expect CLM-03   404 GET  "/api/v1/claims/$CLAIM_B1" "$shop_a"
expect CLM-03   404 GET  "/api/v1/claims/$MISSING_ID" "$analyst"
expect API-06   400 GET  /api/v1/claims/not-a-uuid "$analyst"

# Claims: updates that must be refused (no data changes)
expect CLM-12   403 PATCH "/api/v1/claims/$CLAIM_A1" "$shop_a"  '{"status":"APPROVED"}'
expect CLM-10   403 PATCH "/api/v1/claims/$CLAIM_A1" "$analyst" '{"amount":10}'
expect CLM-14   404 PATCH "/api/v1/claims/$CLAIM_B1" "$shop_a"  '{"description":"x"}'
expect CLM-15   409 PATCH "/api/v1/claims/$CLAIM_A2" "$shop_a"  '{"description":"x"}'
expect CLM-08   409 PATCH "/api/v1/claims/$CLAIM_A2" "$analyst" '{"status":"UNDER_REVIEW"}'
expect CLM-17   400 PATCH "/api/v1/claims/$CLAIM_A1" "$admin"   "{\"partnerId\":\"$PARTNER_B\"}"

# Policies
expect POL-01   200 GET  /api/v1/policies/POL-2026-000001 "$analyst"
expect POL-02   403 GET  /api/v1/policies/POL-2026-000001 "$shop_a"
expect POL-03   404 GET  /api/v1/policies/POL-2026-999999 "$admin"

echo
echo "Passed: $passed  Failed: $failed"
[ "$failed" -eq 0 ]
