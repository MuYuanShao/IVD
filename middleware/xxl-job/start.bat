@echo off
setlocal
if /I "%~1"=="--worker" goto worker
cscript.exe //nologo "%~dp0..\..\launch-hidden.vbs" "call ""%~f0"" --worker"
exit /b %errorlevel%

:worker
cd /d "%~dp0"
java -jar xxl-job-admin-2.4.1.jar --spring.config.location=file:./application.properties > xxl-job.log 2>&1
exit /b %errorlevel%
