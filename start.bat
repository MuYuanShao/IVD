@echo off
chcp 936 >nul
title IVD Launcher

echo.
echo ==========================================
echo   IVD Smart Lab System v1.0.0-mvp
echo ==========================================
echo.
echo [1/2] Starting backend on port 8080...
start "Backend :8080" cmd /k D:\IVD\start-backend.bat

echo [2/2] Waiting 8s for backend to initialize...
timeout /t 8 /nobreak >nul

echo [2/2] Starting frontend on port 5173...
start "Frontend :5173" cmd /k D:\IVD\start-frontend.bat

echo.
echo ==========================================
echo   Frontend : http://localhost:5173
echo   Backend  : http://localhost:8080
echo   API Docs : http://localhost:8080/swagger-ui.html
echo ==========================================
echo.
echo You can close this window (services keep running).
pause >nul
