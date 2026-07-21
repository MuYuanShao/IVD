@echo off
chcp 936 >nul
title Stop IVD Backend

echo Stopping IVD Backend (port 8080)...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080.*LISTENING"') do (
    echo Killing process PID=%%a...
    taskkill /PID %%a /F >nul 2>&1
)

echo Backend stopped.
pause
