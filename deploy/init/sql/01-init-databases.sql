-- ===========================================================================
-- IVD 一期 MySQL 初始化脚本
-- 说明：
--   1. 本脚本由 MySQL 官方镜像在首次启动时自动执行（挂载到 /docker-entrypoint-initdb.d）
--   2. 仅做"建库 + 授权"，业务表 DDL 由 ivd-app 的 Flyway 自动迁移管理
--   3. XXL-Job Admin 自带建表 SQL，但需要独立的 xxl_job 库存在
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 业务库（ivd）—— 由 compose 环境变量 MYSQL_DATABASE 已创建，这里做兜底 + 字符集确认
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `ivd`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- XXL-Job 调度库
-- 注：XXL-Job Admin 2.4.1 启动时会自动在此库建表（tables_xxl_job.sql），
--     这里只确保库存在并授权，避免 Admin 启动失败。
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `xxl_job`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 授权（用户由 compose MYSQL_USER 创建，此处补授 xxl_job 库权限）
-- 使用当前已存在的 ivd 用户，密码同 MYSQL_PASSWORD
-- ---------------------------------------------------------------------------
-- 注意：MySQL 8 创建用户与授权分离。ivd 用户已由环境变量创建，
-- 这里仅授予 xxl_job 库的权限。
GRANT ALL PRIVILEGES ON `xxl_job`.* TO 'ivd'@'%';
GRANT ALL PRIVILEGES ON `ivd`.*     TO 'ivd'@'%';

FLUSH PRIVILEGES;

-- ---------------------------------------------------------------------------
-- 提示
-- ---------------------------------------------------------------------------
SELECT 'IVD databases initialized: ivd, xxl_job' AS log;
SELECT 'Business tables will be created by Flyway on ivd-app first boot.' AS log;
