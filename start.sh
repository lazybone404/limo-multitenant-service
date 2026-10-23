#!/usr/bin/env bash
# =============================================================================
# Limo full-stack starter (Git Bash)
# Starts: python-core(8001) + java-service(8082) + frontend(5173)
# Usage  : ./start.sh [profile]     profile default = dev
# Logs   : /c/WorkPlace/_temp/limo-*.log
# Style  : warm-close old process  +  curl health-check  +  venv python
# =============================================================================
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="/c/WorkPlace/_temp"
PROFILE="${1:-dev}"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Java/jdk-21.0.11}"
JAVA="$JAVA_HOME/bin/java"
JAR="$ROOT/java-service/target/limo-service-1.0.0.jar"
mkdir -p "$LOG_DIR"

# ---- helpers ----
port_up()  { netstat -ano 2>/dev/null | grep -q ":$1" ; }
kill_port() { # port  (warm close first, force if still busy)
  local pid
  pid=$(netstat -ano 2>/dev/null | grep ":$1" | grep LISTENING | awk '{print $NF}' | sort -u | head -1)
  [ -z "$pid" ] && return 0
  cmd //c "taskkill /PID $pid" >/dev/null 2>&1
  sleep 1
  if port_up "$1"; then
    echo "  [~] force close :$1 (pid $pid)"
    cmd //c "taskkill /F /PID $pid" >/dev/null 2>&1
  else
    echo "  [ok] closed     :$1"
  fi
}
wait_up() { # port name  (real response, not just socket)
  local n=0 code
  until code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$1"); [ "$code" != "000" ]; do
    n=$((n + 1)); [ $n -gt 60 ] && { echo "  [x] $2 timeout (:$1)"; return 1; }
    sleep 1
  done
  echo "  [ok] $2 up (:$1)"
}

echo "== Limo starter =="

# ---- MySQL probe (3306) ----
if port_up 3306; then
  echo "  [ok] MySQL running :3306"
else
  echo "  [!] MySQL NOT running - start it first (e.g.  net start MySQL80)"
fi

# ---- python-core ----
kill_port 8001
PY="python"
[ -x "$ROOT/python-core/.venv/Scripts/python.exe" ] && PY="$ROOT/python-core/.venv/Scripts/python.exe"
( cd "$ROOT/python-core" && nohup "$PY" -m uvicorn app:app --host 0.0.0.0 --port 8001 >"$LOG_DIR/limo-python.log" 2>&1 & )
wait_up 8001 python-core

# ---- java-service ----
kill_port 8082
if [ ! -f "$JAR" ]; then
  echo "  .. building java jar (first run)"
  ( cd "$ROOT/java-service" && ./mvnw -q clean package -DskipTests )
fi
( cd "$ROOT/java-service" && nohup "$JAVA" -jar target/limo-service-1.0.0.jar --spring.profiles.active="$PROFILE" >"$LOG_DIR/limo-java.log" 2>&1 & )
wait_up 8082 java-service

# ---- frontend ----
kill_port 5173
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  echo "  .. npm install (first run)"
  ( cd "$ROOT/frontend" && npm install --registry=https://registry.npmmirror.com )
fi
( cd "$ROOT/frontend" && nohup npm run dev >"$LOG_DIR/limo-frontend.log" 2>&1 & )
wait_up 5173 frontend

echo "== done =="
echo "  backend  : http://localhost:8082"
echo "  frontend : http://localhost:5173"
echo "  (stop with ./stop.sh)"
