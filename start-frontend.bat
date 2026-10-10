@echo off
chcp 65001 >nul
setlocal
set "IVD_ROOT=%~dp0"
if /I "%~1"=="--worker" goto worker
cscript.exe //nologo "%IVD_ROOT%launch-hidden.vbs" "call ""%~f0"" --worker"
exit /b %errorlevel%

:worker
title IVD Frontend :5173
set "WEB_DIR=%~dp0ivd-web"
where npm.cmd >nul 2>&1 || (echo [ERROR] npm.cmd not found in PATH.& exit /b 1)
where node.exe >nul 2>&1 || (echo [ERROR] node.exe not found in PATH.& exit /b 1)
if not exist "%WEB_DIR%\node_modules" (echo [ERROR] Run npm ci in %WEB_DIR% first.& exit /b 1)
cd /d "%WEB_DIR%"
echo Building IVD production frontend...
call npm.cmd run build > "%WEB_DIR%\frontend-build.log" 2>&1
if errorlevel 1 (echo [ERROR] Frontend build failed. Check ivd-web\frontend-build.log.& exit /b 1)
echo Starting IVD production frontend on http://127.0.0.1:5173
node.exe "%WEB_DIR%\scripts\serve-production.mjs" > "%WEB_DIR%\frontend.log" 2>&1
exit /b %errorlevel%
