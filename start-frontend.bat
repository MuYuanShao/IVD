@echo off
chcp 936 >nul
title IVD Frontend :5173

echo.
echo ==========================================
echo   IVD Frontend (Vite)
echo   Port: 5173
echo ==========================================
echo.
echo Starting Vite dev server in background...
echo Log: D:\IVD\ivd-web\frontend.log
echo.

cd /d D:\IVD\ivd-web
npm run dev > "D:\IVD\ivd-web\frontend.log" 2>&1
