@echo off
chcp 936 >nul
title Stop IVD Frontend

echo Stopping IVD Frontend (port 5173)...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173.*LISTENING"') do (
    echo Killing process PID=%%a...
    taskkill /PID %%a /F >nul 2>&1
)

echo Frontend stopped.
pause
