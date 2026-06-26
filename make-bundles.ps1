# make-bundles.ps1 — produce shareable zips of the dockerized app.
#
#   .\make-bundles.ps1               # build both bundles (default)
#   .\make-bundles.ps1 -Mode source  # only the small source bundle
#   .\make-bundles.ps1 -Mode offline # only the large offline image bundle
#
# Output goes to .\bundles\
#   customs-rms-source.zip   - project + Docker files; recipient runs `docker compose up --build`
#                              (needs Docker + internet on the target machine)
#   customs-rms-offline.zip  - pre-built images + offline compose; recipient runs `docker load`
#                              then `docker compose -f docker-compose.offline.yml up -d`
#                              (needs only Docker; works air-gapped)

param(
  [ValidateSet("source", "offline", "both")]
  [string]$Mode = "both"
)

$ErrorActionPreference = "Stop"
$root    = $PSScriptRoot
$out     = Join-Path $root "bundles"
$staging = Join-Path $env:TEMP "rms-bundle-staging"
New-Item -ItemType Directory -Force -Path $out | Out-Null

function Build-SourceBundle {
  Write-Host "==> Building SOURCE bundle..." -ForegroundColor Cyan
  $stage = Join-Path $staging "customs-rms"
  if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $stage | Out-Null

  # robocopy mirrors the repo but skips heavy/generated/private folders.
  # robocopy exit codes 0-7 mean success; 8+ is a real error.
  robocopy $root $stage /E `
    /XD "node_modules" "target" "dist" ".git" "bundles" "playwright-report" "test-results" ".idea" ".vscode" ".m2" `
    /XF "*.log" "images.tar" ".env" `
    /NFL /NDL /NJH /NJS /NP | Out-Null
  if ($LASTEXITCODE -ge 8) { throw "robocopy failed with code $LASTEXITCODE" }

  $zip = Join-Path $out "customs-rms-source.zip"
  if (Test-Path $zip) { Remove-Item $zip -Force }
  Compress-Archive -Path "$stage\*" -DestinationPath $zip -CompressionLevel Optimal
  Remove-Item $stage -Recurse -Force
  $size = (Get-Item $zip).Length / 1MB
  Write-Host ("    -> {0}  ({1:N1} MB)" -f $zip, $size) -ForegroundColor Green
}

function Build-OfflineBundle {
  Write-Host "==> Building OFFLINE bundle..." -ForegroundColor Cyan

  docker image inspect customs-rms-backend:latest  *> $null
  if (-not $?) { throw "Image customs-rms-backend:latest not found. Run 'docker compose up --build' first." }
  docker image inspect customs-rms-frontend:latest *> $null
  if (-not $?) { throw "Image customs-rms-frontend:latest not found. Run 'docker compose up --build' first." }

  $stage = Join-Path $staging "customs-rms-offline"
  if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $stage | Out-Null

  Write-Host "    saving images to images.tar (this is the slow part, ~1-2 min)..."
  docker save customs-rms-backend:latest customs-rms-frontend:latest mysql:8.0 -o (Join-Path $stage "images.tar")
  if (-not $?) { throw "docker save failed" }

  Copy-Item (Join-Path $root "packaging\docker-compose.offline.yml") $stage
  Copy-Item (Join-Path $root "packaging\README.md") $stage
  Copy-Item (Join-Path $root "packaging\run.ps1") $stage
  Copy-Item (Join-Path $root "packaging\run.sh") $stage

  $zip = Join-Path $out "customs-rms-offline.zip"
  if (Test-Path $zip) { Remove-Item $zip -Force }
  Write-Host "    compressing (slow; images barely compress)..."
  Compress-Archive -Path "$stage\*" -DestinationPath $zip -CompressionLevel Fastest
  Remove-Item $stage -Recurse -Force
  $size = (Get-Item $zip).Length / 1MB
  Write-Host ("    -> {0}  ({1:N0} MB)" -f $zip, $size) -ForegroundColor Green
}

if ($Mode -eq "source"  -or $Mode -eq "both") { Build-SourceBundle }
if ($Mode -eq "offline" -or $Mode -eq "both") { Build-OfflineBundle }

Write-Host ""
Write-Host "All done. Bundles are in: $out" -ForegroundColor Green
