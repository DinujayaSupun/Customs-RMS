#!/usr/bin/env bash
# run.sh — one-shot launcher for the OFFLINE image bundle (Linux/macOS server).
# Usage:  chmod +x run.sh && ./run.sh
# Requires: Docker Engine + the compose plugin installed and running.
set -euo pipefail
cd "$(dirname "$0")"

echo "==> Checking Docker is running..."
docker info >/dev/null 2>&1 || { echo "Docker is not running/installed."; exit 1; }

if [ -f ./images.tar ]; then
  echo "==> Loading images from images.tar (one time, ~1-2 min)..."
  docker load -i ./images.tar
else
  echo "images.tar not found next to this script — are you in the unzipped bundle folder?"
  exit 1
fi

echo "==> Starting containers..."
docker compose -f docker-compose.offline.yml up -d

echo
echo "Done. Open http://localhost:3000  (login: admin / admin123)"
echo "Stop with:  docker compose -f docker-compose.offline.yml down"
