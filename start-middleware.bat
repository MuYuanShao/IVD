@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
if /I "%~1"=="--worker" goto worker
cscript.exe //nologo "%ROOT%launch-hidden.vbs" "call ""%~f0"" --worker" "%ROOT%logs\middleware-launcher.log"
exit /b %errorlevel%

:worker
title IVD Middleware
set "REDIS=%ROOT%middleware\redis"
set "MINIO=%ROOT%middleware\minio"
set "XXL=%ROOT%middleware\xxl-job"
set "HIDDEN_LAUNCHER=%ROOT%launch-hidden.vbs"
echo [1/3] Redis
netstat -ano | findstr /R /C:":6379 .*LISTENING" >nul 2>&1
if errorlevel 1 (
  sc query Redis >nul 2>&1
  if errorlevel 1 (
    if not exist "%REDIS%\redis-server.exe" (echo [ERROR] Redis is missing.& exit /b 1)
    cscript.exe //nologo "%HIDDEN_LAUNCHER%" "cd /d ""%REDIS%"" && redis-server.exe redis.windows.conf" "%REDIS%\redis.log"
  ) else (sc start Redis >nul 2>&1)
)
for /L %%i in (1,1,20) do (
  netstat -ano | findstr /R /C:":6379 .*LISTENING" >nul 2>&1 && goto redis_ready
  ping 127.0.0.1 -n 2 >nul
)
echo [ERROR] Redis startup timed out.& exit /b 1
:redis_ready
echo   Ready: 127.0.0.1:6379
echo [2/3] MinIO
if not exist "%MINIO%\minio.exe" (echo [ERROR] MinIO executable is missing.& exit /b 1)
netstat -ano | findstr /R /C:":9000 .*LISTENING" >nul 2>&1
if errorlevel 1 (
  if not exist "%MINIO%\data" mkdir "%MINIO%\data"
  cscript.exe //nologo "%HIDDEN_LAUNCHER%" "cd /d ""%MINIO%"" && set MINIO_ROOT_USER=ivdadmin&& set MINIO_ROOT_PASSWORD=Minio@123456&& minio.exe server data --address :9000 --console-address :9001" "%MINIO%\minio.log"
)
for /L %%i in (1,1,30) do (
  curl.exe --noproxy "*" -fsS "http://127.0.0.1:9000/minio/health/live" >nul 2>&1 && goto minio_ready
  ping 127.0.0.1 -n 2 >nul
)
echo [ERROR] MinIO startup timed out.& exit /b 1
:minio_ready
echo   Ready: http://127.0.0.1:9000
echo [3/3] XXL-Job
where java.exe >nul 2>&1 || (echo [ERROR] Java not found.& exit /b 1)
if not exist "%XXL%\xxl-job-admin-2.4.1.jar" (echo [ERROR] XXL-Job JAR is missing.& exit /b 1)
if not exist "%XXL%\application.properties" (echo [ERROR] XXL-Job configuration is missing.& exit /b 1)
netstat -ano | findstr /R /C:":8081 .*LISTENING" >nul 2>&1
if errorlevel 1 (
  cscript.exe //nologo "%HIDDEN_LAUNCHER%" "cd /d ""%XXL%"" && java -jar xxl-job-admin-2.4.1.jar --spring.config.location=file:./application.properties" "%XXL%\xxl-job.log"
)
for /L %%i in (1,1,60) do (
  curl.exe --noproxy "*" -fsS "http://127.0.0.1:8081/xxl-job-admin/toLogin" >nul 2>&1 && goto xxl_ready
  ping 127.0.0.1 -n 2 >nul
)
echo [ERROR] XXL-Job startup timed out.& exit /b 1
:xxl_ready
echo   Ready: http://127.0.0.1:8081/xxl-job-admin
exit /b 0
