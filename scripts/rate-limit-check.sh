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

echo "Sending up to $MAX_CALLS requests as '$user'..."
for ((i = 1; i <= MAX_CALLS; i++)); do
  status="$(curl -s -o "$tmp/body" -D "$tmp/headers" -w '%{http_code}' \
    -H "Authorization: Bearer $token" "$API_URL/api/v1/claims" || true)"

  if [ "$status" = "429" ]; then
    retry_after="$(sed -n 's/^[Rr]etry-[Aa]fter: *\([0-9]*\).*/\1/p' "$tmp/headers" | tr -d '\r')"
    echo "Got 429 on request $i"

    ok=true
    if [ -n "$retry_after" ] && [ "$retry_after" -ge 1 ]; then
      echo "PASS  Retry-After: $retry_after"
    else
      echo "FAIL  missing or invalid Retry-After header"; ok=false
    fi
    if grep -qi '^Content-Type: *application/problem+json' "$tmp/headers"; then
      echo "PASS  Content-Type: application/problem+json"
    else
      echo "FAIL  Content-Type is not application/problem+json"; ok=false
    fi
    if grep -q 'rate-limit-exceeded' "$tmp/body"; then
      echo "PASS  Problem type rate-limit-exceeded"
    else
      echo "FAIL  body has no rate-limit-exceeded problem type"; ok=false
    fi
    if $ok; then exit 0; else exit 1; fi
  fi

  if [ "$status" != "200" ]; then
    echo "FAIL  request $i returned $status before any 429" >&2
    exit 1
  fi
done

echo "FAIL  no 429 after $MAX_CALLS requests" >&2
exit 1
