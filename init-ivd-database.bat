@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
set "MYSQL="
for /f "delims=" %%p in ('where mysql.exe 2^>nul') do if not defined MYSQL set "MYSQL=%%p"
if not defined MYSQL if exist "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
if not defined MYSQL if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not defined MYSQL (echo [ERROR] mysql.exe not found.& exit /b 1)
echo Creating ivd and xxl_job databases...
"%MYSQL%" -h 127.0.0.1 -u root -p123456789 -e "CREATE DATABASE IF NOT EXISTS ivd CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE IF NOT EXISTS xxl_job CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'ivd'@'localhost' IDENTIFIED BY 'Ivd@123456'; ALTER USER 'ivd'@'localhost' IDENTIFIED BY 'Ivd@123456'; GRANT ALL PRIVILEGES ON ivd.* TO 'ivd'@'localhost'; GRANT ALL PRIVILEGES ON xxl_job.* TO 'ivd'@'localhost'; FLUSH PRIVILEGES;" || exit /b 1
"%MYSQL%" -h 127.0.0.1 -u ivd -pIvd@123456 -N -s -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='xxl_job' AND table_name='xxl_job_info';" | findstr /X "1" >nul
if errorlevel 1 (
  echo Importing XXL-Job schema...
  "%MYSQL%" -h 127.0.0.1 -u ivd -pIvd@123456 < "%ROOT%middleware\xxl-job\tables_xxl_job.sql" || exit /b 1
) else (echo XXL-Job schema already initialized.)
echo Database initialization complete. Flyway runs when the backend starts.
exit /b 0
