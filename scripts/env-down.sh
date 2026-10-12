#!/usr/bin/env bash
# Stops the local stack. With --volumes, also deletes the data (resets the seed).
# Usage: scripts/env-down.sh [--volumes]
set -euo pipefail

cd "$(dirname "$0")/.."

case "${1:-}" in
  "")        docker compose down ;;
  --volumes) docker compose down --volumes ;;
  *)         echo "usage: $0 [--volumes]" >&2; exit 2 ;;
esac
