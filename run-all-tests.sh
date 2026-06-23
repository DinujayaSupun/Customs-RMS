#!/usr/bin/env bash
#
# run-all-tests.sh — run EVERY test suite locally in one go, the same way CI does:
#   1) backend       (mvn test: unit + integration tests, on in-memory H2)
#   2) frontend unit (vitest)
#   3) frontend e2e  (Playwright against a live backend + MySQL)
#
# Usage (from the repo root, in Git Bash):
#   DB_PASSWORD=your_mysql_root_password bash run-all-tests.sh
#
# Requirements: local MySQL running on :3306, JDK 17, Node 20+, npm.
# Playwright starts the frontend itself; this script starts/stops the backend for e2e.
# Exit code is 0 only if ALL suites pass.

set -uo pipefail

: "${DB_USERNAME:=root}"
: "${DB_PASSWORD:?Set DB_PASSWORD to your local MySQL root password — e.g.  DB_PASSWORD=secret bash run-all-tests.sh}"
: "${JWT_SECRET:=Y2lfdGVzdF9qd3Rfc2VjcmV0XzEyMzQ1Njc4OTBfMTIzNDU2Nzg5MF8xMjM0NTY3ODkwXzEyMzQ1Njc4OTA=}"

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACK="$ROOT/rms-backend"
FRONT="$ROOT/rms-frontend"
failed=()

hdr() { printf '\n============================================================\n==> %s\n============================================================\n' "$1"; }

hdr "[1/3] Backend tests (unit + integration, H2)"
( cd "$BACK" && ./mvnw test ) || failed+=("backend")

hdr "Installing frontend dependencies (npm ci)"
( cd "$FRONT" && npm ci ) || failed+=("npm-ci")

hdr "[2/3] Frontend unit tests (vitest)"
( cd "$FRONT" && npm run test:unit ) || failed+=("frontend-unit")

hdr "[3/3] Frontend e2e (Playwright + live backend)"
export SPRING_PROFILES_ACTIVE=e2e SERVER_PORT=8081
export DB_USERNAME DB_PASSWORD JWT_SECRET
export APP_SEED_ENABLED=true
export APP_SEED_ADMIN_PASSWORD=CiWorkflowAdmin123!
export APP_SEED_DEFAULT_PASSWORD=CiWorkflowPass123!
export RMS_E2E_API_BASE_URL=http://localhost:8081/api
export RMS_E2E_ADMIN_USER=admin RMS_E2E_ADMIN_PASS=CiWorkflowAdmin123!
export RMS_E2E_DC_USER=dc RMS_E2E_DC_PASS=CiWorkflowPass123!

echo "--> starting backend on :8081 (e2e profile — needs MySQL on :3306) ..."
if ( cd "$BACK" && ./mvnw -q spring-boot:start -Dspring-boot.start.wait=1000 -Dspring-boot.start.maxAttempts=120 ); then
  trap '( cd "$BACK" && ./mvnw -q spring-boot:stop >/dev/null 2>&1 ) || true' EXIT
  ( cd "$FRONT" && npx playwright install chromium && npm run test:e2e ) || failed+=("frontend-e2e")
  ( cd "$BACK" && ./mvnw -q spring-boot:stop >/dev/null 2>&1 ) || true
  trap - EXIT
else
  echo "!! backend did not start — check that MySQL is up on :3306 and DB_PASSWORD is correct."
  failed+=("e2e-backend-startup")
fi

hdr "RESULT"
if [ "${#failed[@]}" -eq 0 ]; then
  echo "ALL TEST SUITES PASSED"
  exit 0
else
  echo "FAILED SUITES: ${failed[*]}"
  exit 1
fi
