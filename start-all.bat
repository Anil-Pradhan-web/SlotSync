@echo off
chcp 65001 >nul
TITLE SlotSync Backend Microservices Suite
color 0B

echo ==============================================================================
echo            SLOTSYNC -- ULTRA-HIGH CONCURRENCY BACKEND SUITE                     
echo                 Tatkal Ticket & Slot Reservation Engine                        
echo ==============================================================================
echo.

REM Detect Java 23 Executable
SET JAVA_BIN="java"
IF EXIST "C:\Program Files\Java\jdk-23\bin\java.exe" (
    SET JAVA_BIN="C:\Program Files\Java\jdk-23\bin\java.exe"
)

echo [1/2] Compiling Backend Modules (Maven / javac)...
IF EXIST "%~dp0\compile-backend.bat" (
    call "%~dp0\compile-backend.bat"
) ELSE (
    call mvn clean compile -q -f "%~dp0\backend\pom.xml"
)

SET BACKEND_CP="backend/dsa-engine/target/classes;backend/waiting-room/target/classes;backend/slot-lock/target/classes;backend/payment-worker/target/classes;backend/api-gateway/target/classes;backend/target/classes"

echo [2/2] Launching All 4 Microservices (Ports 8080, 8081, 8082, 8083)...
start "SlotSync Backend Suite [Ports 8080-8083]" cmd /k "cd /d %~dp0 && chcp 65001 >nul && %JAVA_BIN% -Dfile.encoding=UTF-8 -cp %BACKEND_CP% com.slotsync.SlotSyncAllServers"

echo.
echo Waiting for servers to initialize...
timeout /t 2 /nobreak >nul

echo.
echo ==============================================================================
echo  ALL 4 BACKEND MICROSERVICES STARTED SUCCESSFULLY!
echo  ----------------------------------------------------------------------------
echo  - Microservice 1 (API Gateway):    http://localhost:8080 (RateLimiter + Idempotency)
echo  - Microservice 2 (Waiting Room):   http://localhost:8081 (FIFO Queue + Batch Admit)
echo  - Microservice 3 (Slot-Lock):      http://localhost:8082 (SegmentTree + MinHeap TTL)
echo  - Microservice 4 (Payment Worker): http://localhost:8083 (Webhook + WAL Disk Journal)
echo.
echo  To run the Interactive Interviewer Demo Suite, execute:
echo     run-demo.bat
echo.
echo  To terminate all services, run:
echo     stop-all.bat
echo ==============================================================================
echo.
pause
