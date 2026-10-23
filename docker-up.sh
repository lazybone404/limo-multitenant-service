#!/usr/bin/env bash
# docker-up.sh - start limo-app containers (mysql+backend+python-core+frontend)
# usage: ./docker-up.sh     (requires Docker Desktop running)
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker compose up -d --build
echo "[OK] limo-app started -> http://localhost:5174  (api 8082 / python 8001)"
