@echo off
setlocal
if /I "%~1"=="--worker" goto worker
cscript.exe //nologo "%~dp0..\..\launch-hidden.vbs" "call ""%~f0"" --worker"
exit /b %errorlevel%

:worker
set "MINIO_ROOT_USER=ivdadmin"
set "MINIO_ROOT_PASSWORD=Minio@123456"
if not exist "%~dp0data" mkdir "%~dp0data"
"%~dp0minio.exe" server "%~dp0data" --address :9000 --console-address :9001 > "%~dp0minio.log" 2>&1
exit /b %errorlevel%
