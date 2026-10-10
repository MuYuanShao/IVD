@echo off
chcp 65001 >nul
setlocal
set "IVD_ROOT=%~dp0"
if /I "%~1"=="--worker" goto worker
cscript.exe //nologo "%IVD_ROOT%launch-hidden.vbs" "call ""%~f0"" --worker"
exit /b %errorlevel%

:worker
title IVD Backend :8080
set "JAR=%IVD_ROOT%ivd-app\ivd-server\target\ivd-server-1.0.0-mvp.jar"
set "LOG=%IVD_ROOT%ivd-app\ivd-server\backend.log"
where java.exe >nul 2>&1 || (echo [ERROR] Java 17 not found in PATH.& exit /b 1)
if not exist "%JAR%" (echo [ERROR] Missing backend JAR: %JAR%& exit /b 1)
if not defined JWT_SECRET set "JWT_SECRET=28841e0c7d10a6ab5d45e8e0e08af19d5e5e4bbf3a4e1a8c5d7a9b3f4e6c8a1b"
if not defined SPRING_DATASOURCE_USERNAME set "SPRING_DATASOURCE_USERNAME=ivd"
if not defined SPRING_DATASOURCE_PASSWORD set "SPRING_DATASOURCE_PASSWORD=Ivd@123456"
if not defined SPRING_DATA_REDIS_PASSWORD set "SPRING_DATA_REDIS_PASSWORD=Redis@123456"
if not defined XXL_JOB_EXECUTOR_LOGPATH set "XXL_JOB_EXECUTOR_LOGPATH=%IVD_ROOT%logs\xxl-job"
echo Starting IVD backend on http://127.0.0.1:8080
cd /d "%IVD_ROOT%ivd-app\ivd-server"
java -jar "%JAR%" > "%LOG%" 2>&1
exit /b %errorlevel%
