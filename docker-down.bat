@echo off
rem docker-down.bat - double-click to stop limo-app containers
cd /d "%~dp0"
echo [down] stopping limo-app containers ...
docker compose down
echo [OK] stopped (data volumes kept).
timeout /t 3 >nul
