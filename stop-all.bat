@echo off
chcp 65001 >nul
TITLE SlotSync Stop Controller
color 0C

echo ==============================================================================
echo                      STOPPING ALL SLOTSYNC BACKEND SERVICES                   
echo ==============================================================================
echo.

powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8080,8081,8082,8083 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }; Write-Host '[OK] Microservice ports 8080, 8081, 8082, 8083 terminated.'"

echo.
echo All backend microservices have been cleanly terminated.
echo ==============================================================================
pause
