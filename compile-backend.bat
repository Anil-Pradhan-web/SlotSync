@echo off
TITLE SlotSync Zero-Dependency Compiler
color 0A

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0compile-backend.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)
