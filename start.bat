@echo off
chcp 65001 >nul
cd /d "%~dp0"
rem ============================================
rem limo-app - one-click start (double-click)
rem starts python-core(8001)+java-service(8082)+frontend(5173)
rem Force Git Bash (NOT WSL) - avoid C:\Windows\System32\bash.exe (WSL)
rem ============================================
set "GB=C:\Program Files\Git\bin\bash.exe"
if not exist "%GB%" set "GB=C:\Program Files (x86)\Git\bin\bash.exe"
if exist "%GB%" (
  echo [start] limo-app via Git Bash: %GB%
  "%GB%" -c "bash start.sh"
) else (
  echo [ERROR] Git Bash not found at default path.
  echo Please open Git Bash and run: cd limo-app ^&^& ./start.sh
)
echo.
pause
