# IVD 智慧实验室管理系统 — 基座构建清单

> **版本**: V1.0-BASE
> **日期**: 2026-07-03
> **状态**: ✅ 基座搭建完成
> **依赖**: 《IVD智慧实验室管理系统_一期MVP_PRD》V1.2 + 《IVD智慧实验室管理系统_开发规范手册》V1.0-DEV
> **密级**: 内部

---

## 目录

- [1. 总览](#1-总览)
- [2. 后端清单](#2-后端清单)
- [3. 前端清单](#3-前端清单)
- [4. 数据库清单](#4-数据库清单)
- [5. 部署清单](#5-部署清单)
- [6. 模块依赖关系](#6-模块依赖关系)
- [7. 待实现功能清单（按 Sprint）](#7-待实现功能清单按-sprint)
- [8. 文件统计](#8-文件统计)
- [附录 A：技术栈对照](#附录-a技术栈对照)

---

## 1. 总览

```
工作目录:  D:\IVD\
├── ivd-app/                  ← 后端（Spring Boot 3 多模块）   [69 个文件]
├── ivd-web/                  ← 前端（Vue 3 + Element Plus）  [27 个文件]
├── deploy/                   ← 部署编排（Docker Compose）    [已有，未变动]
├── IVD智慧实验室管理系统_一期MVP_PRD.md
├── IVD智慧实验室管理系统_开发规范手册.md
├── 一期MVP效果图_v2.html
└── IVD智慧实验室管理系统_基座构建清单.md  ← 本文件
```

---

## 2. 后端清单

### 2.1 父工程

| 文件 | 路径 | 状态 |
|------|------|:---:|
| 父 POM | `ivd-app/pom.xml` | ✅ |
| Spring Boot 3.2.7 / JDK 17 / Maven 3.9 | — | ✅ |
| 14 个 module 声明 | — | ✅ |
| 依赖版本统一管理（MyBatis-Plus / Flyway / XXL-Job / MinIO / JWT / Hutool / Knife4j） | — | ✅ |

### 2.2 ivd-common（共享内核）— 无依赖

| 分类 | 类名 | 文件 | 说明 | 状态 |
|------|------|------|------|:---:|
| **枚举（15 个）** | `MaterialTypeEnum` | `enums/MaterialTypeEnum.java` | ★ 物料类型 — 决定全流程行为的分流字段 | ✅ |
| | `ApprovalStatusEnum` | `enums/ApprovalStatusEnum.java` | 目录审批状态（DRAFT/PENDING/APPROVED/REJECTED） | ✅ |
| | `UdiStatusEnum` | `enums/UdiStatusEnum.java` | UDI 批次生命周期 11 状态 + 终态标记 | ✅ |
| | `SettlementStatusEnum` | `enums/SettlementStatusEnum.java` | X2 结算状态（PENDING/SETTLED/REVERSED） | ✅ |
| | `SettlementTypeEnum` | `enums/SettlementTypeEnum.java` | 结算类型（REQUISITION/CONSUMPTION） | ✅ |
| | `AuditDecisionEnum` | `enums/AuditDecisionEnum.java` | 审核决策 4 动作 + 优先级 | ✅ |
| | `LicenseStatusEnum` | `enums/LicenseStatusEnum.java` | 证照状态 | ✅ |
| | `LicenseTypeEnum` | `enums/LicenseTypeEnum.java` | 证照类型（6 种） | ✅ |
| | `PurchaseOrderStatusEnum` | `enums/PurchaseOrderStatusEnum.java` | 采购订单状态 | ✅ |
| | `RequisitionStatusEnum` | `enums/RequisitionStatusEnum.java` | 申领单状态（8 种） | ✅ |
| | `DeviceBindModeEnum` | `enums/DeviceBindModeEnum.java` | 设备关联策略（FORCE/FLEXIBLE） | ✅ |
| | `UrgencyEnum` | `enums/UrgencyEnum.java` | 紧急程度（NORMAL/URGENT/CRITICAL） | ✅ |
| | `ConsumptionPurposeEnum` | `enums/ConsumptionPurposeEnum.java` | 消耗用途 | ✅ |
| | `AcceptanceResultEnum` | `enums/AcceptanceResultEnum.java` | 验收结果 | ✅ |
| | `FiveCodeMappingStatusEnum` | `enums/FiveCodeMappingStatusEnum.java` | 五码映射状态 | ✅ |
| | `ShortageStatusEnum` | `enums/ShortageStatusEnum.java` | 缺货单状态 | ✅ |
| **异常（7 个）** | `BizException` | `exception/BizException.java` | 基类（errorCode + message + detail） | ✅ |
| | `IllegalStateTransitionException` | `exception/IllegalStateTransitionException.java` | 非法状态转移（40001） | ✅ |
| | `DuplicateSettlementException` | `exception/DuplicateSettlementException.java` | 重复结算（40900） | ✅ |
| | `InsufficientStockException` | `exception/InsufficientStockException.java` | 库存不足（40002） | ✅ |
| | `LicenseExpiredException` | `exception/LicenseExpiredException.java` | 证照过期（40003） | ✅ |
| | `UdiMissingException` | `exception/UdiMissingException.java` | UDI 缺失（40004） | ✅ |
| | `ApprovalPendingException` | `exception/ApprovalPendingException.java` | 审批中冲突（40901） | ✅ |
| **DTO** | `Result<T>` | `dto/Result.java` | 统一响应体（code/message/data/timestamp） | ✅ |
| | `PageVO<T>` | `dto/PageVO.java` | 统一分页（total/page/pageSize/records） | ✅ |
| **基础类** | `BaseEntity` | `base/BaseEntity.java` | 实体基类（id/createdAt/updatedAt） | ✅ |
| **配置（3 个）** | `MybatisPlusConfig` | `config/MybatisPlusConfig.java` | 分页插件 + MapperScan | ✅ |
| | `JacksonConfig` | `config/JacksonConfig.java` | 日期格式 + 时区 + 忽略未知属性 | ✅ |
| | `GlobalExceptionHandler` | `config/GlobalExceptionHandler.java` | @RestControllerAdvice 统一异常处理 | ✅ |
| **工具类（4 个）** | `IdGenerator` | `utils/IdGenerator.java` | Hutool 雪花 ID | ✅ |
| | `BizNoGenerator` | `utils/BizNoGenerator.java` | 业务单号格式（REQ-/SH-/PO-/STL-/BATCH-CONS-/IVD-） | ✅ |
| | `RedisKeys` | `utils/RedisKeys.java` | Redis Key 常量（device_batches/batch/settlement_lock/license_expire） | ✅ |
| | `PhiUtils` | `utils/PhiUtils.java` | 患者 ID SHA-256 哈希脱敏 + 日志掩码 | ✅ |

### 2.3 ivd-catalog（M01 试剂目录 + 五码映射）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-catalog/pom.xml`（依赖 ivd-common） | ✅ |
| Entity | `entity/ReagentCatalog.java`（全字段映射 + @TableName） | ✅ |
| Mapper | `mapper/ReagentCatalogMapper.java`（BaseMapper + pageList） | ✅ |
| Service 接口 | `service/ReagentCatalogService.java`（CRUD + 审批方法签名） | ✅ |
| Service 实现 | `service/impl/ReagentCatalogServiceImpl.java`（骨架 + TODO） | ⚠️ 待实现 |
| Controller | `controller/ReagentCatalogController.java`（REST 端点定义） | ⚠️ 待实现 |

### 2.4 ivd-license（M03 证照管理）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-license/pom.xml`（依赖 ivd-common + ivd-catalog + MinIO） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.5 ivd-procurement（M04 采购+申领+验收）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-procurement/pom.xml`（依赖 ivd-common + ivd-catalog + ivd-license） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.6 ivd-inventory（M06 三级库+FEFO）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-inventory/pom.xml`（依赖 ivd-common + ivd-catalog） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.7 ivd-consumption（M08 上机与消耗）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-consumption/pom.xml`（依赖 ivd-common + ivd-catalog + ivd-inventory） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.8 ivd-settlement（X2 结算）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-settlement/pom.xml`（依赖 ivd-common + ivd-consumption） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.9 ivd-finance（M09 日清月结）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-finance/pom.xml`（依赖 ivd-common + ivd-settlement） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.10 ivd-analytics（S1 效益分析）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-analytics/pom.xml`（依赖 ivd-common + ivd-finance） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.11 ivd-rules（S2 统一规则引擎）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-rules/pom.xml`（依赖 ivd-common） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.12 ivd-audit（S3 批次感知自动审核）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-audit/pom.xml`（依赖 ivd-common + ivd-rules + ivd-consumption） | ✅ |
| Controller | `controller/AuditController.java`（接口 A/B/C 端点定义） | ⚠️ 待实现 |
| Service | `service/AuditPipelineService.java`（审核流水线骨架 + 批次关联方法签名） | ⚠️ 待实现 |

### 2.13 ivd-udi（X1 UDI 追溯引擎）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-udi/pom.xml`（依赖 ivd-common） | ✅ |
| Entity | `entity/UdiStatusLog.java`（不可变日志，APPEND-ONLY） | ✅ |
| Service | `service/UdiStateMachine.java`（状态转移骨架 + 校验框架） | ⚠️ 待实现 |

### 2.14 ivd-integration（LIS/HIS 接口适配）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-integration/pom.xml`（依赖 ivd-common） | ✅ |
| 目录骨架 | `controller/ service/ mapper/ entity/ dto/ vo/ enums/ config/ event/` | ✅ 空目录 |

### 2.15 ivd-server（启动入口）

| 文件 | 路径 | 状态 |
|------|------|:---:|
| POM | `ivd-server/pom.xml`（聚合全部 13 个模块 + Spring Boot + Flyway + XXL-Job + Actuator） | ✅ |
| 主类 | `server/IvdApplication.java`（@SpringBootApplication scanBasePackages） | ✅ |
| 安全配置 | `server/config/SecurityConfig.java`（JWT 无状态 + 公开接口白名单） | ⚠️ JWT Filter 待实现 |
| XXL-Job | `server/config/XxlJobConfig.java`（执行器注册） | ✅ |
| 主配置 | `resources/application.yml`（MySQL/Redis/MinIO/Flyway/XXL-Job/JWT/Actuator/Knife4j） | ✅ |
| Dockerfile | `Dockerfile`（eclipse-temurin:17-jre-alpine） | ✅ |

---

## 3. 前端清单

### 3.1 工程配置

| 文件 | 说明 | 状态 |
|------|------|:---:|
| `package.json` | Vue 3.4 / Element Plus 2.x / ECharts 5.x / Pinia / Vue Router / Axios / Vite 5 | ✅ |
| `vite.config.js` | 别名 `@` + API 代理 `/api` → `localhost:8080` + Element Plus 自动导入 | ✅ |
| `index.html` | 入口 HTML | ✅ |
| `src/main.js` | createApp + Pinia + Router + Element Plus（中文）+ 图标全局注册 | ✅ |
| `src/App.vue` | 根组件（router-view） | ✅ |
| `src/assets/global.css` | 全局 CSS 变量（主色/图表色/状态色/库存色 + 脉动动画） | ✅ |

### 3.2 路由 & API & 状态

| 文件 | 说明 | 状态 |
|------|------|:---:|
| `src/router/index.js` | 完整路由表（10 模块 + 登录 + 驾驶舱 + 布局嵌套） | ✅ |
| `src/utils/request.js` | Axios 封装（JWT 注入 / 401 跳转 / 统一错误处理） | ✅ |
| `src/api/catalog.js` | M01 试剂目录 API（CRUD + 审批） | ✅ |
| `src/api/audit.js` | S3 自动审核 API（接口 A/B/C） | ✅ |
| `src/api/procurement.js` | M04 采购管理 API | ✅ |
| `src/api/inventory.js` | M06 三级库 API | ✅ |
| `src/stores/catalog.js` | Pinia Store（catalog 列表状态） | ✅ |
| `src/utils/format.js` | 金额千分位 / 日期 / 状态标签格式化 | ✅ |
| `src/utils/validate.js` | UDI-DI / 注册证号 / 医保编码校验规则 | ✅ |

### 3.3 页面组件

| 文件 | 路由 | 说明 | 状态 |
|------|------|------|:---:|
| `views/login/LoginView.vue` | `/login` | 登录页（表单 + 校验） | ✅ |
| `views/layout/LayoutView.vue` | `/` | 主布局（侧边栏菜单 + 顶栏 + 内容区） | ✅ |
| `views/dashboard/DashboardView.vue` | `/dashboard` | 主任驾驶舱（KPI 卡 + ECharts 占位 + 行动建议） | ⚠️ 图表待实现 |
| `views/catalog/CatalogList.vue` | `/catalog` | ★ 试剂目录完整页面（统计卡 + 筛选 + Tab + 表格 + 分页 + badge 动画） | ⚠️ 后端联调 |
| `views/license/LicenseList.vue` | `/license` | 证照管理占位 | 🔲 占位 |
| `views/procurement/ProcurementList.vue` | `/procurement` | 采购管理占位 | 🔲 占位 |
| `views/inventory/InventoryView.vue` | `/inventory` | 三级库占位 | 🔲 占位 |
| `views/consumption/ConsumptionView.vue` | `/consumption` | 上机消耗占位 | 🔲 占位 |
| `views/settlement/SettlementList.vue` | `/settlement` | 结算管理占位 | 🔲 占位 |
| `views/finance/FinanceView.vue` | `/finance` | 日清月结占位 | 🔲 占位 |
| `views/analytics/AnalyticsView.vue` | `/analytics` | 效益分析占位 | 🔲 占位 |
| `views/audit/AuditView.vue` | `/audit` | 自动审核占位 | 🔲 占位 |

---

## 4. 数据库清单

### 4.1 Flyway 迁移脚本（7 个）

| 脚本 | 版本号 | 说明 | 状态 |
|------|--------|------|:---:|
| `V20260701001__init_reagent_catalog.sql` | V1 | M01 试剂目录主表 + 五码映射 + 审批日志 + UDI 待补全追踪 | ✅ |
| `V20260701002__init_supplier_license.sql` | V2 | M03 供应商 + 证照主表 + 变更记录 + 试剂供应商关联 | ✅ |
| `V20260701003__init_procurement.sql` | V3 | M04 申领单 + 缺货单 + 采购订单 + 明细（含拆分字段 acceptance_method） | ✅ |
| `V20260701004__init_inventory_warehouse.sql` | V4 | M06/M08 仓库 + 设备 + 批次 + 上机记录（★ device_batch_load 关键索引） | ✅ |
| `V20260701005__init_consumption_settlement.sql` | V5 | M08/X2/M09 消耗记录 + 结算记录 + 每日进销存快照 | ✅ |
| `V20260701006__init_audit_udi.sql` | V6 | S2/S3/X1 审核规则（内置 9 条规则 INSERT） + 决策日志 + UDI 状态日志（按月分区） | ✅ |
| `V20260701007__init_reserved_tables.sql` | V7 | 二期预留：qc_result / poct_device / ai_consultation_log（空表） | ✅ |

### 4.2 一期核心表（15 张）

| 序号 | 表名 | 模块 | 主键策略 | 说明 |
|:---:|------|------|---------|------|
| 1 | `reagent_catalog` | M01 | AUTO_INCREMENT | 物料字典（含 material_type, tests_per_kit, price_per_unit） |
| 2 | `five_code_mapping` | M01 | AUTO_INCREMENT | 五码映射（仅 REAGENT/CALIBRATOR/QC_MATERIAL） |
| 3 | `catalog_approval_log` | M01 | 雪花 ID | 试剂目录审批日志（JSON 存 field_changes） |
| 4 | `udi_backlog` | M01 | AUTO_INCREMENT | UDI 待补全追踪 |
| 5 | `supplier` | M03 | AUTO_INCREMENT | 供应商主表 |
| 6 | `supplier_license` | M03 | 雪花 ID | 证照主表（JSON 存 attachments） |
| 7 | `license_change_log` | M03 | 雪花 ID | 证照变更记录 |
| 8 | `reagent_supplier_rel` | M03 | AUTO_INCREMENT | 试剂-供应商关联 |
| 9 | `purchase_requisition` | M04 | 雪花 ID | 申领单主表 |
| 10 | `purchase_requisition_item` | M04 | 雪花 ID | 申领明细（含拣货结果） |
| 11 | `shortage_order` | M04 | 雪花 ID | 缺货单主表 |
| 12 | `shortage_order_item` | M04 | 雪花 ID | 缺货明细 |
| 13 | `purchase_order` | M04 | 雪花 ID | 采购订单主表（含 source_type, acceptance_method） |
| 14 | `purchase_order_item` | M04 | 雪花 ID | 采购明细（含 material_type 分流） |
| 15 | `warehouse` | M06 | AUTO_INCREMENT | 三级库仓库（自引用树） |
| 16 | `device` | M08 | AUTO_INCREMENT | 设备注册 |
| 17 | `reagent_batch` | M06 | 雪花 ID | ★ 批次（核心生命周期实体，含 fefo_priority） |
| 18 | `device_batch_load` | M08 | 雪花 ID | ★ 上机记录（自动审核命脉，联合索引 device_id,load_time,unload_time） |
| 19 | `consumption_record` | M08 | 雪花 ID | 消耗记录（含 INBOUND/CONSUME 两种类型） |
| 20 | `settlement_record` | X2 | 雪花 ID | 结算记录（X2 第一层结算） |
| 21 | `daily_inventory_snapshot` | M09 | 雪花 ID | 每日进销存快照 |
| 22 | `audit_rule` | S2 | AUTO_INCREMENT | 审核规则定义（内置 9 条规则 + JSON scope/condition） |
| 23 | `audit_decision_log` | S3 | 雪花 ID | 审核决策日志 |
| 24 | `udi_status_log` | X1 | 雪花 ID | ★ UDI 状态流转日志（APPEND-ONLY, RANGE 按月分区） |

### 4.3 预留表（二期填充）

| 表名 | 说明 |
|------|------|
| `qc_result` | 质控结果 — TODO: Phase 2 |
| `poct_device` | POCT 设备 — TODO: Phase 2 |
| `ai_consultation_log` | AI 调用日志 — TODO: Phase 3 |

### 4.4 关键索引

| 索引 | 用途 | 性能目标 |
|------|------|:---:|
| `idx_device_batch_load (device_id, load_time, unload_time)` | 批次关联时间区间匹配 | — |
| `idx_udi_log_batch (batch_no, occurred_at)` | UDI 追溯查询 | — |
| `idx_audit_log_date (decided_at, decision)` | 审核日志查询 | — |
| `idx_fefo (warehouse_id, fefo_priority, expiry_date)` | FEFO 出库推荐 | — |
| `idx_status_expire (status, expire_date)` | 证照效期查询 | — |

---

## 5. 部署清单

| 项目 | 路径/文件 | 状态 |
|------|----------|:---:|
| Docker Compose 编排 | `deploy/docker-compose.yml` | ✅ 已有 |
| 环境变量模板 | `deploy/.env.example` | ✅ 已有 |
| Nginx 配置 | `deploy/nginx/nginx.conf + proxy-common.conf` | ✅ 已有 |
| MySQL 参数 | `deploy/init/mysql/my.cnf` | ✅ 已有 |
| 建库脚本 | `deploy/init/sql/01-init-databases.sql` | ✅ 已有 |
| 部署文档 | `deploy/README.md` | ✅ 已有 |
| **App Dockerfile** | `ivd-app/ivd-server/Dockerfile` | ✅ 新增 |

### 5.1 服务清单

| 服务 | 镜像 | 端口 | 健康检查依赖 |
|------|------|------|------|
| mysql | `mysql:8.0` | 3306 | — |
| redis | `redis:7-alpine` | 6379 | — |
| minio | `minio/minio:latest` | 9000, 9001 | — |
| minio-init | `minio/mc:latest` | — | minio healthy |
| xxl-job-admin | `xuxueli/xxl-job-admin:2.4.1` | 8081 | mysql healthy |
| **ivd-app** | `ivd/ivd-app:1.0.0-mvp`（CI 构建） | 8080 | mysql + redis + minio healthy |
| nginx | `nginx:stable-alpine` | 80, 443 | ivd-app healthy |

---

## 6. 模块依赖关系

```
                    ivd-common（无依赖，被所有模块依赖）
                         ↑
        ┌────────────────┼────────────────┐
        ↓                ↓                ↓
   ivd-catalog     ivd-udi          ivd-rules
        ↑                ↑                │
   ivd-license            │                │
        ↑                │                │
   ivd-procurement        │                │
        ↑                │                │
   ivd-inventory ←────────┘                │
        ↑                                 │
   ivd-consumption                         │
        ↑                                 │
   ┌────┴────────────┐                    │
   ↓                 ↓                    ↓
ivd-settlement   ivd-audit ←──────────────┘
   ↓
ivd-finance
   ↓
ivd-analytics

ivd-integration ── 独立，仅依赖 ivd-common
ivd-server ── 聚合全部 13 个模块
```

**规则**：
- ✅ 模块间只能向下依赖（不允许循环依赖）
- ✅ 跨模块调用走 Service 接口，不直接跨模块查 DAO
- ✅ ivd-common 不依赖任何业务模块

---

## 7. 待实现功能清单（按 Sprint）

### S0（W1-2）: 脚手架 — 状态 [✅ 完成]

- [x] Maven 多模块项目结构
- [x] Spring Boot 3.2.7 父 POM
- [x] 14 个 Maven 模块 POM
- [x] ivd-common 共享内核（枚举/异常/DTO/工具类）
- [x] ivd-server 启动入口 + 配置
- [x] Flyway 7 个迁移脚本（24 张表）
- [x] Dockerfile
- [x] Vue 3 前端脚手架
- [ ] CI 配置（GitHub Actions / Jenkins）
- [ ] Checkstyle / ESLint 规则文件

### S1（W3-4）: M01 试剂目录 — 状态 [🔲 待开始]

- [ ] `ReagentCatalogServiceImpl` 全部 CRUD 实现
- [ ] 五码映射 CRUD
- [ ] 审批流完整实现（DRAFT → PENDING → APPROVED/REJECTED）
- [ ] UDI 待补全清单面板
- [ ] 试剂目录列表前端联调
- [ ] 新增/编辑物料弹窗（含 material_type 动态显隐）
- [ ] 审批面板 Tab
- [ ] MockMvc API 测试

### S2（W5-6）: X1 UDI 引擎 — 状态 [⚠️ 部分骨架]

- [ ] `UdiStateMachine` 完整状态转移矩阵校验
- [ ] 完整状态转移路径实现（11 状态 × N 路径）
- [ ] `udi_status_log` 不可变写入
- [ ] GS1/Code128/HIBC 码制识别
- [ ] Redis `device_batches` Sorted Set 操作封装
- [ ] 集成测试（状态机全路径遍历）

### S3（W7-8）: M04 采购 + M03 证照 + 验收 — 状态 [🔲 待开始]

- [ ] 申领单 CRUD + 状态流转
- [ ] 缺货单自动生成逻辑
- [ ] 自动拆单引擎（SPLIT-01~04）
- [ ] 采购订单 CRUD + 状态流转
- [ ] 扫码验收流程
- [ ] 数量验收流程（CONSUMABLE/OTHER）
- [ ] 证照 CRUD + MinIO 附件上传
- [ ] 证照效期预警定时任务
- [ ] LIC-02 规则联动（过期冻结采购）

### S4（W9）: M06 三级库 + FEFO — 状态 [🔲 待开始]

- [ ] 仓库自引用树管理
- [ ] FEFO 优先级计算 + 重算定时任务
- [ ] 批次切换 6 条规则（INB-NEW/QC/TAIL/SWITCH/ASK/DEDUP）
- [ ] 库存查询（多维筛选）
- [ ] 库存预警

### S5（W10）: M08 上机消耗 — 状态 [🔲 待开始]

- [ ] 手动扫码上机（UDI + 设备资产码）
- [ ] 手动核销 + 批量核销
- [ ] 设备批次映射 Redis 缓存维护
- [ ] 耗材/其他直接领用消耗
- [ ] 设备关联模式校验（FORCE vs FLEXIBLE）

### S6（W11）: S1 效益分析 — 状态 [🔲 待开始]

- [ ] 三层成本模型计算（标称 vs 实际）
- [ ] 三张核心报表（单测试成本表/品牌对比表/浪费点 Top10）
- [ ] 浪费点定位算法（规则版）
- [ ] 主任驾驶舱 KPI + ECharts 图表
- [ ] HIS 计费数据批量导入

### S7（W12）: S2/S3 规则引擎 + 自动审核 — 状态 [⚠️ 部分骨架]

- [ ] 审核流水线完整实现（L1→L2→L3）
- [ ] Redis 批次关联时间区间匹配
- [ ] 9 条内置规则条件判断逻辑
- [ ] LIS 接口 A/B/C 实现
- [ ] 审核兜底 AUTO_RELEASE
- [ ] 人工反馈记录（误拦截率计算）
- [ ] 集成测试（审核全链路）

### S8（W13）: M09 日清月结 + UAT — 状态 [🔲 待开始]

- [ ] 每日进销存快照定时任务
- [ ] 日结/月结逻辑
- [ ] 差异率计算（<1% 预警）
- [ ] XXL-Job 7 个定时任务实现
- [ ] 全链路联调 5 条核心链路
- [ ] UAT 验收测试

### JWT & 安全

- [ ] JWT Filter 实现
- [ ] 用户登录/刷新 Token API
- [ ] RBAC 权限校验注解
- [ ] IP 白名单校验
- [ ] 审计日志 5W 写入
- [ ] 高危操作二次确认

---

## 8. 文件统计

| 分类 | 数量 | 说明 |
|------|:---:|------|
| 后端 Java 文件 | 20 | 枚举 15 + 异常 7 + DTO 2 + 基础 1 + 配置 3 + 工具 4 + Entity 3 + Mapper 1 + Service 4 + Controller 3 + 主类 1 = 44 个类 |
| 后端 POM | 15 | 父 1 + 模块 14 |
| Flyway 脚本 | 7 | 共 24 张表 |
| 后端配置 | 2 | application.yml + Dockerfile |
| 前端 Vue/JS | 17 | 页面 11 + 路由 1 + API 4 + Store 1 + Utils 3 + 入口 3 |
| 前端配置 | 4 | package.json + vite.config.js + index.html + global.css |
| **合计** | **~96** | |

---

## 附录 A：技术栈对照

| 层 | 组件 | 版本 | 状态 |
|---|------|------|:---:|
| 语言 | Java | JDK 17 (LTS) | ✅ |
| 框架 | Spring Boot | 3.2.7 | ✅ |
| 构建 | Maven | 3.9.x | ✅ |
| ORM | MyBatis-Plus | 3.5.7 | ✅ |
| 数据库 | MySQL | 8.0 (InnoDB / utf8mb4) | ✅ Docker |
| 连接池 | HikariCP | Spring Boot 内置 | ✅ |
| 缓存 | Redis | 7.x | ✅ Docker |
| 对象存储 | MinIO | RELEASE.2024+ | ✅ Docker |
| 任务调度 | XXL-Job | 2.4.1 | ✅ Docker |
| 数据库迁移 | Flyway | 9.x | ✅ |
| 鉴权 | Spring Security + JWT | 6.x | ⚠️ Filter 待实现 |
| 前端框架 | Vue 3 | 3.4 | ✅ |
| UI 库 | Element Plus | 2.x | ✅ |
| 图表 | ECharts | 5.x | ✅ |
| 构建 | Vite | 5.x | ✅ |
| 状态管理 | Pinia | 2.x | ✅ |
| 路由 | Vue Router | 4.x | ✅ |
| HTTP | Axios | 1.x | ✅ |
| 部署 | Docker Compose | v2 | ✅ |

---

> **基座完成日期**: 2026-07-03
> **下一步**: 按 Sprint 计划进入 S1 试剂目录 CRUD 实现
> **维护**: IVD 一期团队
