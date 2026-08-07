@echo off
chcp 65001 >nul
title IVD Middleware Shutdown

echo ============================================
echo   IVD Middleware Shutdown
echo ============================================
echo.

echo [1/3] Stopping Redis...
sc stop Redis >nul 2>&1
echo   Redis stopped

echo [2/3] Stopping MinIO...
taskkill /F /IM minio.exe >nul 2>&1
echo   MinIO stopped

echo [3/3] Stopping XXL-Job...
for /f "tokens=5" %%a in ('netstat -ano ^| find ":8081" ^| find "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
echo   XXL-Job stopped

echo.
echo ============================================
echo   All middleware stopped.
echo ============================================
pause
