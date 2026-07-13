@echo off
chcp 936 >nul
title IVD Frontend :5173

echo.
echo ==========================================
echo   IVD Frontend (Vite)
echo   Port: 5173
echo ==========================================
echo.
echo Starting Vite dev server...
echo.

cd /d D:\IVD\ivd-web
npm run dev

echo.
echo ==========================================
echo Frontend stopped. Press any key to close...
pause >nul
