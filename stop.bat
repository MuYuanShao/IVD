@echo off
chcp 65001 >nul
setlocal EnableExtensions
title Stop IVD Services

set "IVD_ROOT=%~dp0"
echo Stopping IVD services in reverse order...
call "%IVD_ROOT%stop-frontend.bat"
call "%IVD_ROOT%stop-backend.bat"
call "%IVD_ROOT%stop-middleware.bat"
echo All IVD services stopped.
exit /b 0
