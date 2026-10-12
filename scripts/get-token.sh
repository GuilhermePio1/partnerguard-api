#!/usr/bin/env bash
# Prints an access token for a local fixture user (see specs/fixtures.md).
# The password grant is for local development only.
# Usage: scripts/get-token.sh <admin|analyst|shop-a> [password]
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
REALM="${REALM:-partnerguard}"
CLIENT_ID="${CLIENT_ID:-partnerguard-cli}"

if [ $# -lt 1 ]; then
  echo "usage: $0 <admin|analyst|shop-a> [password]" >&2
  exit 2
fi
user="$1"
password="${2:-$user}"

response="$(curl -sS --fail-with-body -X POST \
  "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "username=$user" \
  --data-urlencode "password=$password")" || {
  echo "error: token request for '$user' failed: $response" >&2
  exit 1
}

token="$(printf '%s' "$response" | sed -n 's/.*"access_token" *: *"\([^"]*\)".*/\1/p')"
if [ -z "$token" ]; then
  echo "error: no access_token in Keycloak response" >&2
  exit 1
fi

printf '%s\n' "$token"
