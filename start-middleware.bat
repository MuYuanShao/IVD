@echo off
chcp 65001 >nul
title IVD Middleware Startup

echo ============================================
echo   IVD Middleware Startup
echo ============================================
echo.

:: ── Redis ──────────────────────────────────
echo [1/3] Redis...
sc query Redis | find "RUNNING" >nul 2>&1
if %errorlevel% neq 0 (
    sc start Redis >nul 2>&1
    echo   Redis service started OK
) else (
    echo   Redis already running
)
echo   Port: 6379  ^|  Password: Redis@123456
echo.

:: ── MinIO ──────────────────────────────────
echo [2/3] MinIO...
netstat -ano | find ":9000" | find "LISTENING" >nul 2>&1
if %errorlevel% neq 0 (
    start /MIN "" cmd /c "set MINIO_ROOT_USER=ivdadmin && set MINIO_ROOT_PASSWORD=Minio@123456 && D:\IVD\middleware\minio\minio.exe server D:\IVD\middleware\minio\data --address :9000 --console-address :9001 > D:\IVD\middleware\minio\minio.log 2>&1"
    echo   MinIO starting...
    timeout /t 3 /nobreak >nul
) else (
    echo   MinIO already running
)
echo   API: http://localhost:9000  ^|  Console: http://localhost:9001
echo   User: ivdadmin  ^|  Password: Minio@123456
echo.

:: ── XXL-Job ────────────────────────────────
echo [3/3] XXL-Job Admin...
netstat -ano | find ":8081" | find "LISTENING" >nul 2>&1
if %errorlevel% neq 0 (
    start /MIN "" cmd /c "cd /d D:\IVD\middleware\xxl-job && java -jar xxl-job-admin-2.4.1.jar --spring.config.location=file:application.properties > xxl-job.log 2>&1"
    echo   XXL-Job starting...
    timeout /t 10 /nobreak >nul
) else (
    echo   XXL-Job already running
)
echo   URL: http://localhost:8081/xxl-job-admin
echo   Login: admin / 123456
echo.

echo ============================================
echo   All middleware startup complete.
echo ============================================
pause
