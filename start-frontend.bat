@echo off
chcp 65001 >nul
title IVD Frontend :5173

cd /d D:\IVD\ivd-web

if not exist "node_modules" (
    echo [ERROR] node_modules not found. Run: npm install
    pause
    exit /b 1
)

echo ============================================
echo   IVD Frontend — Vue 3 + Vite
echo   Port: 5173  |  Log: frontend.log
echo ============================================
echo.

echo Starting Vite dev server ...
npm run dev > "D:\IVD\ivd-web\frontend.log" 2>&1

echo.
echo Frontend stopped.
pause
