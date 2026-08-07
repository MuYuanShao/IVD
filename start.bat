@echo off
chcp 65001 >nul
title IVD Launcher
set "IVD_ROOT=%~dp0"

echo.
echo ============================================
echo   IVD Smart Lab System v1.0.0-mvp
echo ============================================
echo.

:: ── Backend ────────────────────────────────
echo [1/3] Starting backend on port 8080 ...
start "IVD Backend :8080" /MIN cmd /c "%IVD_ROOT%start-backend.bat"
echo   Backend starting in minimized window

:: ── Frontend ───────────────────────────────
echo [2/3] Starting frontend on port 5173 ...
start "IVD Frontend :5173" /MIN cmd /c "%IVD_ROOT%start-frontend.bat"
echo   Frontend starting in minimized window

:: ── Wait for backend, then health check ────
echo.
echo [3/3] Waiting for backend to be ready ...
set /a COUNT=0
:waitloop
timeout /t 2 /nobreak >nul
set /a COUNT+=2
curl -s http://localhost:8080/actuator/health 2>nul | find "UP" >nul
if %errorlevel% equ 0 goto :ready
if %COUNT% geq 60 goto :timeout
echo   waiting ... (%COUNT%s)
goto :waitloop

:timeout
echo.
echo   WARNING: Backend did not start within 60s.
echo   Check the backend window for errors.
goto :done

:ready
echo.
echo ============================================
echo   All services started successfully!
echo.
echo   Frontend : http://localhost:5173
echo   Backend  : http://localhost:8080
echo   API Docs : http://localhost:8080/swagger-ui.html
echo ============================================
echo.
echo   Close the backend/frontend windows to stop,
echo   or run stop.bat
echo.

:done
pause
