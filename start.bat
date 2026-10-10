@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
if /I "%~1"=="--worker" goto worker
if not exist "%ROOT%logs" mkdir "%ROOT%logs"
cscript.exe //nologo "%ROOT%launch-hidden.vbs" "call ""%~f0"" --worker" "%ROOT%logs\launcher.log"
exit /b %errorlevel%

:worker
title IVD Launcher
echo ============================================
echo   IVD Smart Lab System v1.0.0-mvp
echo ============================================
echo [1/3] Starting middleware...
call "%ROOT%start-middleware.bat" --worker || exit /b 1
echo [2/3] Starting backend...
curl.exe --noproxy "*" -fsS "http://127.0.0.1:8080/actuator/health" >nul 2>&1
if errorlevel 1 call "%ROOT%start-backend.bat"
for /L %%i in (1,1,60) do (
  curl.exe --noproxy "*" -fsS "http://127.0.0.1:8080/actuator/health" 2>nul | findstr /C:"UP" >nul && goto backend_ready
  ping 127.0.0.1 -n 3 >nul
)
echo [ERROR] Backend startup timed out. Check ivd-app\ivd-server\backend.log& exit /b 1
:backend_ready
echo   Ready: http://127.0.0.1:8080
echo [3/3] Starting frontend...
curl.exe --noproxy "*" -fsS "http://127.0.0.1:5173/login" >nul 2>&1
if errorlevel 1 call "%ROOT%start-frontend.bat"
for /L %%i in (1,1,30) do (
  curl.exe --noproxy "*" -fsS "http://127.0.0.1:5173/login" >nul 2>&1 && goto frontend_ready
  ping 127.0.0.1 -n 2 >nul
)
echo [ERROR] Frontend startup timed out. Check ivd-web\frontend.log& exit /b 1
:frontend_ready
echo   Ready: http://127.0.0.1:5173
echo All services are ready. Run stop.bat to stop them.
exit /b 0
