#!/usr/bin/env bash
# docker-down.sh - stop limo-app containers (keep data volumes)
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker compose down
echo "[OK] limo-app stopped (volumes kept)."
