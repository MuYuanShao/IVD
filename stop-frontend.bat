@echo off
setlocal
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":5173 .*LISTENING" 2^>nul') do powershell.exe -NoProfile -Command "Stop-Process -Id %%p -Force -ErrorAction SilentlyContinue"
echo Frontend stopped.
exit /b 0
