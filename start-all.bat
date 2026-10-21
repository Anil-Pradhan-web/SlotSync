@echo off
TITLE SlotSync Master Controller
color 0B

echo ==============================================================================
echo                      SLOTSYNC MASTER CONTROLLER                              
echo       Ultra-High Concurrency Ticket & Slot Reservation Engine               
echo ==============================================================================
echo.

REM Detect Java 23 Executable
SET JAVA_BIN="java"
IF EXIST "C:\Program Files\Java\jdk-23\bin\java.exe" (
    SET JAVA_BIN="C:\Program Files\Java\jdk-23\bin\java.exe"
)

REM Detect Node / NPM Executable
SET NPM_BIN="npm"
IF EXIST "C:\Program Files\nodejs\npm.cmd" (
    SET NPM_BIN="C:\Program Files\nodejs\npm.cmd"
)

echo [1/3] Starting Backend Microservices (Ports 8080, 8081, 8082, 8083)...
SET BACKEND_CP="backend/dsa-engine/target/classes;backend/waiting-room/target/classes;backend/slot-lock/target/classes;backend/payment-worker/target/classes;backend/api-gateway/target/classes;backend/target/classes"

start "SlotSync Backend Suite [Ports 8080-8083]" cmd /k "cd /d %~dp0 && %JAVA_BIN% -cp %BACKEND_CP% com.slotsync.SlotSyncAllServers"

echo [2/3] Starting Frontend React + Vite Server (Port 5173)...
start "SlotSync Frontend [Port 5173]" cmd /k "cd /d %~dp0\frontend && %NPM_BIN% run dev -- --host 127.0.0.1 --port 5173"

echo.
echo [3/3] Waiting for servers to initialize...
timeout /t 3 /nobreak >nul

echo Opening browser at http://localhost:5173 ...
start http://localhost:5173

echo.
echo ==============================================================================
echo  ALL SERVICES STARTED SUCCESSFULLY!
echo  - API Gateway:    http://localhost:8080
echo  - Waiting Room:   http://localhost:8081
echo  - Slot-Lock:      http://localhost:8082
echo  - Payment Worker: http://localhost:8083
echo  - React Frontend: http://localhost:5173
echo.
echo  To stop all servers anytime, simply run: stop-all.bat
echo ==============================================================================
echo.
pause
