@echo off
chcp 65001 >nul
title IVD Backend :8080

set "JAR=D:\IVD\ivd-app\ivd-server\target\ivd-server-1.0.0-mvp.jar"
set "LOG=D:\IVD\ivd-app\ivd-server\backend.log"

if not exist "%JAR%" (
    echo [ERROR] JAR not found: %JAR%
    echo Please run: mvn package -pl ivd-server -am -DskipTests
    pause
    exit /b 1
)

echo ============================================
echo   IVD Backend — Spring Boot 3.2.7
echo   Port: 8080  |  Log: backend.log
echo ============================================
echo.

set "JWT_SECRET=28841e0c7d10a6ab5d45e8e0e08af19d5e5e4bbf3a4e1a8c5d7a9b3f4e6c8a1b"

echo Starting application ...
java -jar "%JAR%" ^
  --spring.datasource.username=root ^
  --spring.datasource.password=123456789 ^
  --spring.flyway.enabled=false ^
  --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration ^
  > "%LOG%" 2>&1

echo.
echo Backend stopped.
pause
