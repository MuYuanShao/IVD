# IVD 智慧实验室管理系统 — 一期部署包

> 对应 PRD：《IVD 智慧实验室管理系统_一期MVP_PRD》第 9.6 节
> 组件：MySQL 8.0 · Redis 7 · MinIO · XXL-Job Admin · ivd-app · Nginx

---

## 1. 目录结构

```
deploy/
├── docker-compose.yml          # 主编排文件
├── .env.example                # 环境变量模板（拷贝为 .env）
├── README.md                   # 本文件
├── init/
│   ├── mysql/
│   │   └── my.cnf              # MySQL 自定义参数（utf8mb4/慢查询/binlog）
│   └── sql/
│       └── 01-init-databases.sql  # 建库脚本（ivd + xxl_job）
└── nginx/
    ├── nginx.conf              # Nginx 主配置（反向代理 + TLS 预留）
    ├── proxy-common.conf       # 静态资源 / API 代理 / WS 预留
    ├── html/                   # 前端构建产物（CI 构建后放入）
    └── certs/                  # TLS/国密证书（启用 HTTPS 时放入）
```

---

## 2. 环境要求

| 项 | 最低 | 推荐（二甲~三甲） |
|---|---|---|
| OS | CentOS 7 / 麒麟 V10 / 统信 UOS | 麒麟 V10 |
| CPU | 4 核 | 8 核 |
| 内存 | 8 GB | 16-32 GB |
| 磁盘 | SSD 100 GB | SSD 500 GB |
| Docker | 20.10+ | 24+ |
| Compose | v2 | v2.20+ |

> 信创环境：基础镜像请替换为对应 OS 的官方镜像；MySQL 可按 PRD 9.3.3 平替为达梦/OceanBase。

---

## 3. 快速开始（开发/演示环境）

```bash
# 1. 配置环境变量（开发环境用默认密码即可，生产务必改）
cp .env.example .env

# 2. 放入前端构建产物（首次可放空目录占位）
mkdir -p nginx/html

# 3. 启动全部服务
docker compose up -d

# 4. 观察启动日志
docker compose logs -f ivd-app

# 5. 健康检查（全部 healthy 即就绪）
docker compose ps
```

**默认访问地址**：
| 服务 | 地址 | 账号 |
|---|---|---|
| Web 前端 | http://localhost | — |
| 后端 API | http://localhost/api/actuator/health | — |
| MinIO 控制台 | http://localhost:9001 | ivdadmin / Minio@123456 |
| XXL-Job 控制台 | http://localhost:8081/xxl-job-admin | admin / 123456 |
| MySQL | localhost:3306 | ivd / Ivd@123456 |

---

## 4. 生产部署清单（医院交付前逐项确认）

- [ ] `.env` 中所有密码已修改（MYSQL / REDIS / MINIO / XXL_JOB）
- [ ] `IVD_APP_IMAGE` 改为实际镜像 tag（CI 构建产物）
- [ ] `nginx/html` 已放入前端构建产物
- [ ] 启用 HTTPS：取消 `nginx.conf` 中 443 段注释，放入证书到 `nginx/certs/`
- [ ] 数据卷做异地备份（MySQL 每日全量 + binlog；MinIO 跨节点副本）
- [ ] 完成等保二级配置（防火墙、端口最小化暴露、审计日志留存）
- [ ] 医院内网网段配置到 `proxy-common.conf` 的 actuator 白名单
- [ ] XXL-Job 控制台默认密码 `admin/123456` 已修改

---

## 5. 关键配置说明

### 5.1 数据库表如何创建？
**业务表由 Flyway 自动迁移，不在 init/sql 里。** ivd-app 首次启动时，Flyway 会扫描 classpath 的迁移脚本，自动建表（含 PRD 第 7 章全部表）。`init/sql/01-init-databases.sql` 只负责建库（ivd + xxl_job）和授权。

### 5.2 XXL-Job 任务如何注册？
1. 访问 XXL-Job 控制台 → 任务管理
2. 新建执行器：AppName = `ivd-job-executor`，注册方式自动
3. 任务示例（PRD 10.1 对应）：
   - `ivd:daily-settlement`（每日 23:59 进销存快照）
   - `ivd:fefo-recalculate`（每日 02:00 FEFO 重算）
   - `ivd:license-warn`（每日 09:00 证照效期预警）
   - `ivd:month-report`（每月 1 日 09:00 月结）

### 5.3 审核兜底机制（PRD 8.6）
ivd-app 启动参数 `IVD_AUDIT_FALLBACK_RELEASE=true`：当审核服务异常时，**默认放行结果（AUTO_RELEASE）**，避免本系统故障卡住医院出报告。这是医疗安全底线，生产环境保持开启。

### 5.4 信创切换（MySQL → 达梦/OceanBase）
按 PRD 9.3.3 路径：
1. 替换 `docker-compose.yml` 中 mysql 服务镜像为达梦/OceanBase 镜像
2. 修改 `SPRING_DATASOURCE_URL` 驱动与端口
3. MyBatis-Plus `DbType` 切换方言
4. JDBC 驱动 jar 随 ivd-app 镜像一起替换

---

## 6. 运维命令速查

```bash
# 启停
docker compose up -d              # 启动全部
docker compose down               # 停止并移除容器（保留数据）
docker compose restart ivd-app    # 重启单个服务

# 数据备份
docker compose exec mysql mysqldump -uivd -pIvd@123456 --single-transaction ivd > backup_ivd_$(date +%Y%m%d).sql

# 数据恢复
docker compose exec -i mysql mysql -uivd -pIvd@123456 ivd < backup_ivd_20260618.sql

# 查看日志
docker compose logs -f ivd-app          # 应用日志
docker compose logs -f --tail=200 mysql # MySQL 日志

# 进入容器
docker compose exec mysql bash
docker compose exec ivd-app sh

# 资源占用
docker stats
```

---

## 7. 故障排查

| 现象 | 排查方向 |
|---|---|
| ivd-app 启动失败：Connection refused mysql | MySQL 未就绪，检查 `docker compose ps` 是否 healthy；Flyway 在等 DB |
| ivd-app 报 xxl_job 库不存在 | `01-init-databases.sql` 未执行（仅首次启动 MySQL 空数据卷时执行）；手动跑脚本 |
| 审核接口超时（>30s） | 查 Redis 是否健康、批次关联缓存是否命中；临时调大 nginx `proxy_read_timeout` |
| MinIO 上传 403 | 检查 bucket 是否由 minio-init 创建（`docker compose logs minio-init`） |
| 前端访问白屏 | `nginx/html` 未放构建产物，或路径不对 |
| 容器时间不对 | 确认宿主机时区为 CST；compose 已设 TZ=Asia/Shanghai |
| 磁盘满 | 清理 mysql binlog（`PURGE BINARY LOGS`）、Docker 无用镜像（`docker system prune`） |

---

## 8. 与 PRD 的对应关系

| PRD 章节 | 本部署包实现 |
|---|---|
| 9.3 技术栈 | docker-compose.yml 全部组件版本对齐 |
| 9.4 信创适配 | README §5.4 信创切换路径 |
| 9.5 数据库约定 | my.cnf（utf8mb4/binlog/慢查询）、01-init-databases.sql |
| 9.6 部署拓扑 | docker-compose.yml 服务编排 |
| 8.6 审核兜底 | ivd-app 环境变量 IVD_AUDIT_FALLBACK_RELEASE |
| 10.1 定时任务 | README §5.2 XXL-Job 任务清单 |

---

> 维护：IVD 一期团队 · 对应 PRD V1.0-MVP
