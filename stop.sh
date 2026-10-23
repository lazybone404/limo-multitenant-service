#!/usr/bin/env bash
# =============================================================================
# Limo full-stack stopper (Git Bash)
# Stops: frontend(5173) + java-service(8082) + python-core(8001)
# =============================================================================
set -u

stop_one() { # port  (warm close first, force if still busy)
  local pid
  pid=$(netstat -ano 2>/dev/null | grep ":$1" | grep LISTENING | awk '{print $NF}' | sort -u | head -1)
  if [ -z "$pid" ]; then
    echo "  [.] not running :$1"
    return 0
  fi
  cmd //c "taskkill /PID $pid" >/dev/null 2>&1
  sleep 1
  if netstat -ano 2>/dev/null | grep -q ":$1"; then
    echo "  [~] force close :$1 (pid $pid)"
    cmd //c "taskkill /F /PID $pid" >/dev/null 2>&1
  else
    echo "  [ok] stopped     :$1"
  fi
}

echo "== Limo stopper =="
stop_one 5173
stop_one 8082
stop_one 8001
echo "== done =="
