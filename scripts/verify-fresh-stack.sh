#!/usr/bin/env bash
# Rebuilds the whole stack from an empty database and runs every end-to-end check against it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
echo "==> Resetting stack (removes the database volume)"
docker compose --profile frontend down -v --remove-orphans
docker compose --profile frontend up -d --build
echo "==> API smoke test through the gateway"
node scripts/e2e-smoke.mjs
echo "==> Browser end-to-end tests"
(cd frontend && npx playwright test)
echo "==> All end-to-end checks passed"
