@echo off
rem docker-up.bat - double-click to start limo-app containers
cd /d "%~dp0"
echo [up] starting limo-app containers ...
docker compose up -d --build
if errorlevel 1 (
  echo [FAIL] compose up failed - is Docker Desktop running?
  pause
  exit /b 1
)
echo [OK] started. Open http://localhost:5174  (api 8082)
timeout /t 3 >nul
