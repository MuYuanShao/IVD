@echo off
chcp 936 >nul
title IVD Backend :8080

echo.
echo ==========================================
echo   IVD Backend Service
echo   Port: 8080
echo ==========================================
echo.
echo Starting Spring Boot in background...
echo Log: D:\IVD\ivd-app\ivd-server\backend.log
echo.

set JAR=D:\IVD\ivd-app\ivd-server\target\ivd-server-1.0.0-mvp.jar
set LOG=D:\IVD\ivd-app\ivd-server\backend.log

REM JWT 签名密钥（生产环境请更换此值）
set JWT_SECRET=28841e0c7d10a6ab5d45e8e0e08af19d5e5e4bbf3a4e1a8c5d7a9b3f4e6c8a1b

javaw -jar %JAR% --spring.datasource.username=root --spring.datasource.password=123456789 --spring.flyway.enabled=false --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration > "%LOG%" 2>&1
