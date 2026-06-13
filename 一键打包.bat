@echo off
PowerShell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-exe.ps1"
if %ERRORLEVEL% EQU 0 (
    echo.
    echo [Done] Build success!
    pause
) else (
    echo.
    echo [Error] Build failed! Please run build-exe.ps1 in PowerShell.
    pause
    exit /b 1
)
