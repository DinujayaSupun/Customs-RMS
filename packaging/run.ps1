# run.ps1 — one-shot launcher for the OFFLINE image bundle (Windows).
# Usage: right-click > Run with PowerShell, or:  powershell -ExecutionPolicy Bypass -File run.ps1
# Requires: Docker Desktop installed and running.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "==> Checking Docker is running..." -ForegroundColor Cyan
docker info *> $null
if (-not $?) { Write-Host "Docker is not running. Start Docker Desktop and re-run." -ForegroundColor Red; exit 1 }

if (Test-Path ".\images.tar") {
    Write-Host "==> Loading images from images.tar (one time, ~1-2 min)..." -ForegroundColor Cyan
    docker load -i ".\images.tar"
} else {
    Write-Host "images.tar not found next to this script — are you in the unzipped bundle folder?" -ForegroundColor Red
    exit 1
}

Write-Host "==> Starting containers..." -ForegroundColor Cyan
docker compose -f docker-compose.offline.yml up -d

Write-Host ""
Write-Host "Done. Open http://localhost:3000  (login: admin / admin123)" -ForegroundColor Green
Write-Host "Stop with:  docker compose -f docker-compose.offline.yml down"
