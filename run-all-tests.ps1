# run-all-tests.ps1 - run EVERY test suite locally in one go (backend + frontend unit + e2e).
#
# Usage (PowerShell, from the repo root):
#   .\run-all-tests.ps1 -DbPassword root
#   (or)  $env:DB_PASSWORD="root"; .\run-all-tests.ps1
#
# If PowerShell blocks the script ("running scripts is disabled"), run it as:
#   powershell -ExecutionPolicy Bypass -File .\run-all-tests.ps1 -DbPassword root
#
# Requires: local MySQL on :3306, JDK 17, Node 20+, npm.
# Playwright starts the frontend itself; this script starts/stops the backend for e2e.

param(
  [string]$DbPassword = $env:DB_PASSWORD,
  [string]$DbUsername = $env:DB_USERNAME
)

$ErrorActionPreference = "Continue"
if (-not $DbUsername) { $DbUsername = "root" }

if (-not $DbPassword) {
  Write-Host "Set your MySQL password first, e.g.:  .\run-all-tests.ps1 -DbPassword root" -ForegroundColor Red
  exit 1
}

$root   = $PSScriptRoot
$back   = Join-Path $root "rms-backend"
$front  = Join-Path $root "rms-frontend"
$failed = @()

function Hdr($t) {
  Write-Host ""
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host "==> $t" -ForegroundColor Cyan
  Write-Host "============================================================" -ForegroundColor Cyan
}

# 1) Backend tests (unit + integration on in-memory H2 - no MySQL needed here)
Hdr "[1/3] Backend tests (unit + integration, H2)"
Push-Location $back
.\mvnw.cmd test
if ($LASTEXITCODE -ne 0) { $failed += "backend" }
Pop-Location

# Frontend dependencies (once)
Hdr "Installing frontend dependencies (npm ci)"
Push-Location $front
npm ci
if ($LASTEXITCODE -ne 0) { $failed += "npm-ci" }
Pop-Location

# 2) Frontend unit tests
Hdr "[2/3] Frontend unit tests (vitest)"
Push-Location $front
npm run test:unit
if ($LASTEXITCODE -ne 0) { $failed += "frontend-unit" }
Pop-Location

# 3) Frontend e2e (needs MySQL + a live backend)
Hdr "[3/3] Frontend e2e (Playwright + live backend)"
$env:SPRING_PROFILES_ACTIVE = "e2e"
$env:SERVER_PORT            = "8081"
$env:DB_USERNAME            = $DbUsername
$env:DB_PASSWORD            = $DbPassword
if (-not $env:JWT_SECRET) {
  $env:JWT_SECRET = "Y2lfdGVzdF9qd3Rfc2VjcmV0XzEyMzQ1Njc4OTBfMTIzNDU2Nzg5MF8xMjM0NTY3ODkwXzEyMzQ1Njc4OTA="
}
$env:APP_SEED_ENABLED          = "true"
$env:APP_SEED_ADMIN_PASSWORD   = "CiWorkflowAdmin123!"
$env:APP_SEED_DEFAULT_PASSWORD = "CiWorkflowPass123!"
$env:RMS_E2E_API_BASE_URL      = "http://localhost:8081/api"
$env:RMS_E2E_ADMIN_USER        = "admin"
$env:RMS_E2E_ADMIN_PASS        = "CiWorkflowAdmin123!"
$env:RMS_E2E_DC_USER           = "dc"
$env:RMS_E2E_DC_PASS           = "CiWorkflowPass123!"

Write-Host "--> starting backend on :8081 (e2e profile - needs MySQL on :3306) ..."
Push-Location $back
.\mvnw.cmd -q spring-boot:start "-Dspring-boot.start.wait=1000" "-Dspring-boot.start.maxAttempts=120"
$startOk = ($LASTEXITCODE -eq 0)
Pop-Location

if ($startOk) {
  try {
    Push-Location $front
    npx playwright install chromium
    npm run test:e2e
    if ($LASTEXITCODE -ne 0) { $failed += "frontend-e2e" }
    Pop-Location
  } finally {
    Push-Location $back
    .\mvnw.cmd -q spring-boot:stop
    Pop-Location
  }
} else {
  Write-Host "!! backend did not start - check MySQL is up on :3306 and the password is correct." -ForegroundColor Red
  $failed += "e2e-backend-startup"
}

# Summary
Hdr "RESULT"
if ($failed.Count -eq 0) {
  Write-Host "ALL TEST SUITES PASSED" -ForegroundColor Green
  exit 0
} else {
  Write-Host ("FAILED SUITES: " + ($failed -join ", ")) -ForegroundColor Red
  exit 1
}
