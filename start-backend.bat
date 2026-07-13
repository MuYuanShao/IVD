@echo off
chcp 936 >nul
title IVD Backend :8080

echo.
echo ==========================================
echo   IVD Backend Service
echo   Port: 8080
echo ==========================================
echo.
echo Starting Spring Boot...
echo.

java -jar D:\IVD\ivd-app\ivd-server\target\ivd-server-1.0.0-mvp.jar --spring.datasource.username=root --spring.datasource.password=123456789 --spring.flyway.enabled=false --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration

echo.
echo ==========================================
echo Backend stopped. Press any key to close...
pause >nul
