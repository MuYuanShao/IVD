@echo off
chcp 65001 >nul
setlocal

echo ============================================================
echo  IVD 一期 MVP — 数据库初始化脚本
echo  用途: 创建 ivd 用户、数据库、执行 Flyway 迁移
echo ============================================================
echo.

set /p MYSQL_ROOT_PWD="请输入 MySQL root 密码: "

echo.
echo [1/3] 创建 ivd 用户和数据库 ...
mysql -u root -p"%MYSQL_ROOT_PWD%" -e "CREATE DATABASE IF NOT EXISTS ivd CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'ivd'@'localhost' IDENTIFIED BY 'Ivd@123456'; GRANT ALL PRIVILEGES ON ivd.* TO 'ivd'@'localhost'; FLUSH PRIVILEGES;" 2>&1
if errorlevel 1 (
  echo ❌ 创建用户/数据库失败，请检查 root 密码是否正确
  pause
  exit /b 1
)
echo ✅ ivd 用户和数据库创建成功

echo.
echo [2/3] 执行 Flyway 迁移脚本 (7个SQL文件) ...
set MIG_DIR=%~dp0ivd-app\ivd-server\src\main\resources\db\migration

for %%f in ("%MIG_DIR%\V20260701001__init_reagent_catalog.sql" "%MIG_DIR%\V20260701002__init_supplier_license.sql" "%MIG_DIR%\V20260701003__init_procurement.sql" "%MIG_DIR%\V20260701004__init_inventory_warehouse.sql" "%MIG_DIR%\V20260701005__init_consumption_settlement.sql" "%MIG_DIR%\V20260701006__init_audit_udi.sql" "%MIG_DIR%\V20260701007__init_reserved_tables.sql") do (
  echo   执行 %%~nxf ...
  mysql -u ivd -p"Ivd@123456" ivd < %%f 2>&1
  if errorlevel 1 (
    echo ❌ 迁移失败: %%~nxf
    pause
    exit /b 1
  )
)
echo ✅ 全部 7 个迁移脚本执行成功

echo.
echo [3/3] 验证 ...
mysql -u ivd -p"Ivd@123456" ivd -e "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='ivd' ORDER BY TABLE_NAME;" 2>&1

echo.
echo ============================================================
echo  数据库初始化完成！
echo  数据库: ivd
echo  用户:   ivd / Ivd@123456
echo ============================================================
pause
