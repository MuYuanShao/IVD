@echo off
setlocal
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":8081 .*LISTENING" 2^>nul') do powershell.exe -NoProfile -Command "Stop-Process -Id %%p -Force -ErrorAction SilentlyContinue"
echo XXL-Job stopped.
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":9000 .*LISTENING" 2^>nul') do powershell.exe -NoProfile -Command "Stop-Process -Id %%p -Force -ErrorAction SilentlyContinue"
echo MinIO stopped.
sc query Redis >nul 2>&1
if errorlevel 1 (
  for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":6379 .*LISTENING" 2^>nul') do powershell.exe -NoProfile -Command "Stop-Process -Id %%p -Force -ErrorAction SilentlyContinue"
  echo Bundled Redis stopped.
) else (
  sc stop Redis >nul 2>&1
  ping 127.0.0.1 -n 2 >nul
  netstat -ano | findstr /R /C:":6379 .*LISTENING" >nul 2>&1
  if errorlevel 1 (echo Redis service stopped.) else (echo [WARN] Redis service is still running; stop it from an elevated terminal if required.)
)
exit /b 0
