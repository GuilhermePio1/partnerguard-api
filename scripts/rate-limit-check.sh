#!/usr/bin/env bash
# Sends GET /api/v1/claims as one user until the API answers 429, then checks
# the Retry-After header and the Problem Details body (spec RL-10).
# This empties the user's bucket: wait about a minute before reusing the user.
# Usage: scripts/rate-limit-check.sh [user]
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
MAX_CALLS="${MAX_CALLS:-100}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
user="${1:-shop-a}"

tmp="$(mktemp -d)"
# Git Bash on Windows: give native tools (curl.exe) a C:/... path.
if command -v cygpath >/dev/null 2>&1; then tmp="$(cygpath -m "$tmp")"; fi
trap 'rm -rf "$tmp"' EXIT

token="$("$SCRIPT_DIR/get-token.sh" "$user")"

# All requests go through one curl process on one connection. Starting a
# process per request can take half a second on Windows, slower than the
# bucket refills (about one token per second), so the bucket might never
# empty. Requests after the first 429 are refused as well and take no tokens
# (ADR-0022), so sending them all is harmless.
urls=()
for ((i = 1; i <= MAX_CALLS; i++)); do urls+=("$API_URL/api/v1/claims"); done

echo "Sending up to $MAX_CALLS requests as '$user'..."
# Each response body is followed by a marker line: status|content type|Retry-After.
curl -s -H "Authorization: Bearer $token" \
  -w '\n@@response %{http_code}|%{content_type}|%header{retry-after}\n' \
  "${urls[@]}" > "$tmp/responses" || true

i=0
body=""
while IFS= read -r line; do
  case "$line" in
    "@@response "*) ;;
    *) body+="$line"; continue ;;
  esac
  i=$((i + 1))
  IFS='|' read -r status content_type retry_after <<< "${line#@@response }"
  retry_after="$(printf '%s' "$retry_after" | tr -d '\r')"

  if [ "$status" = "429" ]; then
    echo "Got 429 on request $i"

    ok=true
    if [ -n "$retry_after" ] && [ "$retry_after" -ge 1 ] 2>/dev/null; then
      echo "PASS  Retry-After: $retry_after"
    else
      echo "FAIL  missing or invalid Retry-After header"; ok=false
    fi
    case "$content_type" in
      application/problem+json*) echo "PASS  Content-Type: application/problem+json" ;;
      *) echo "FAIL  Content-Type is not application/problem+json"; ok=false ;;
    esac
    case "$body" in
      *rate-limit-exceeded*) echo "PASS  Problem type rate-limit-exceeded" ;;
      *) echo "FAIL  body has no rate-limit-exceeded problem type"; ok=false ;;
    esac
    if $ok; then exit 0; else exit 1; fi
  fi

  if [ "$status" != "200" ]; then
    echo "FAIL  request $i returned $status before any 429" >&2
    exit 1
  fi
  body=""
done < "$tmp/responses"

echo "FAIL  no 429 after $MAX_CALLS requests" >&2
exit 1
