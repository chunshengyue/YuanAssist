@echo off
setlocal

cd /d "%~dp0"

set "PORT=8899"
set "URL=http://127.0.0.1:8899"
set "LOG_FILE=%~dp0tools\yuanassist_test_tool\last_start.log"

where python >nul 2>nul
if errorlevel 1 (
    echo [YuanAssist Test Tool] Python was not found in PATH.
    echo Please install Python or add it to PATH.
    pause
    exit /b 1
)

echo [YuanAssist Test Tool] Starting local dashboard...
echo [YuanAssist Test Tool] URL: %URL%
echo [YuanAssist Test Tool] Close this window to stop the server.
echo [YuanAssist Test Tool] Log: %LOG_FILE%

start "" "%URL%"
python -m tools.yuanassist_test_tool ui --port %PORT% > "%LOG_FILE%" 2>&1

echo.
echo [YuanAssist Test Tool] Server stopped.
echo [YuanAssist Test Tool] Last log:
type "%LOG_FILE%"
pause
