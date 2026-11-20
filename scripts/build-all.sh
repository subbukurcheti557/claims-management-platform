#!/usr/bin/env bash
# Builds every service independently (each has its own pom; there is no parent build), then the frontend.
# Usage: ./scripts/build-all.sh [--skip-tests] [--no-frontend]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SKIP_TESTS=""
FRONTEND=1
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS="-DskipTests" ;;
    --no-frontend) FRONTEND=0 ;;
  esac
done

# On Windows behind TLS-inspecting antivirus/proxies, Java must use the Windows certificate store.
if [[ "${OS:-}" == "Windows_NT" ]]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStoreType=Windows-ROOT"
fi

SERVICES=(platform/discovery-server platform/api-gateway services/identity-service services/policy-service services/claims-service services/notification-service)
for svc in "${SERVICES[@]}"; do
  echo "==> Building $svc"
  (cd "$ROOT/backend/$svc" && mvn -B -q package $SKIP_TESTS)
done

if [[ $FRONTEND -eq 1 ]]; then
  echo "==> Building frontend"
  (cd "$ROOT/frontend" && npm ci --no-audit --no-fund && npm run build)
fi

echo "Build complete. Start with: docker compose up -d --build   (add --profile frontend for the UI container)"
