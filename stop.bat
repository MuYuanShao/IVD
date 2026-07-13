@echo off
chcp 65001 >nul
title IVD — 停止服务

echo.
echo  ╔══════════════════════════════════════════════╗
echo  ║         停 止  IVD 所 有 服 务               ║
echo  ╚══════════════════════════════════════════════╝
echo.

echo [1] 停止后端 (8080)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080.*LISTENING 2^>nul') do (
    taskkill /f /pid %%a 2>nul && echo     后端已停止 (PID %%a)
)
if %errorlevel% neq 0 echo     后端未运行

echo [2] 停止前端 (5173)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5173.*LISTENING 2^>nul') do (
    taskkill /f /pid %%a 2>nul && echo     前端已停止 (PID %%a)
)
if %errorlevel% neq 0 echo     前端未运行

echo.
echo 所有服务已停止。按任意键关闭...
pause >nul
