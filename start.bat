@echo off
chcp 936 >nul
title IVD Launcher
set "IVD_ROOT=%~dp0"

echo.
echo ==========================================
echo   IVD Smart Lab System v1.0.0-mvp
echo ==========================================
echo.

echo [1/2] Starting backend on port 8080 (background)...
cscript //nologo "%IVD_ROOT%launch-hidden.vbs" "%IVD_ROOT%start-backend.bat" "D:\IVD\ivd-app\ivd-server\launcher.log"

echo [2/2] Waiting 10s for backend to initialize...
timeout /t 10 /nobreak >nul

echo [2/2] Starting frontend on port 5173 (background)...
cscript //nologo "%IVD_ROOT%launch-hidden.vbs" "%IVD_ROOT%start-frontend.bat" "D:\IVD\ivd-web\launcher.log"

echo.
echo ==========================================
echo   Frontend : http://localhost:5173
echo   Backend  : http://localhost:8080
echo   API Docs : http://localhost:8080/swagger-ui.html
echo ==========================================
echo.
echo Services are running in background (no windows).
echo.
echo   stop-backend.bat   — stop backend
echo   stop-frontend.bat  — stop frontend
echo.
echo You can close this window.
pause >nul
