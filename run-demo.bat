@echo off
chcp 65001 >nul
TITLE SlotSync Interactive Terminal Demo
color 0B

echo ==============================================================================
echo            SLOTSYNC -- INTERACTIVE TERMINAL DEMONSTRATION SUITE
echo ==============================================================================
echo.

SET JAVA_BIN="java"
IF EXIST "C:\Program Files\Java\jdk-23\bin\java.exe" (
    SET JAVA_BIN="C:\Program Files\Java\jdk-23\bin\java.exe"
)

IF EXIST "%~dp0\compile-backend.bat" (
    call "%~dp0\compile-backend.bat"
)

SET BACKEND_CP="backend/dsa-engine/target/classes;backend/waiting-room/target/classes;backend/slot-lock/target/classes;backend/payment-worker/target/classes;backend/api-gateway/target/classes;backend/target/classes"

echo Launching Interactive Demonstration CLI...
%JAVA_BIN% -Dfile.encoding=UTF-8 -cp %BACKEND_CP% com.slotsync.SlotSyncDemoCLI

pause
