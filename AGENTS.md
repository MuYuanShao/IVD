# AGENTS.md — IVD 智慧实验室管理系统

本文件面向在本仓库中工作的 AI 编码助手（Claude Code、GitHub Copilot、Codex 等），说明项目背景、构建/运行方式、代码规范与常见坑，帮助其快速、正确地完成任务。

> 详细规范以下列文档为准，本文件是浓缩摘要，冲突时以原文档为准：
> - [IVD智慧实验室管理系统_一期MVP_PRD.md](IVD智慧实验室管理系统_一期MVP_PRD.md) — 产品需求（模块定义、状态机、枚举值全集）
> - [IVD智慧实验室管理系统_开发规范手册.md](IVD智慧实验室管理系统_开发规范手册.md) — 代码规范、Git 工作流、Code Review 清单
> - [IVD智慧实验室管理系统_基座构建清单.md](IVD智慧实验室管理系统_基座构建清单.md) — 基座搭建任务清单

## 项目概述

IVD（体外诊断）智慧实验室管理系统，一期 MVP 覆盖试剂目录、证照管理、采购申领、三级库存（FEFO）、上机消耗、结算、日清月结、效益分析、批次感知自动审核、UDI 全流程追溯等模块。当前一期为**单体应用**（Maven 多模块，非微服务）。

## 技术栈

| 层 | 组件 | 版本 |
|---|---|---|
| 后端语言/框架 | Java 17 + Spring Boot | 3.2.7 |
| 构建 | Maven | 3.9.x |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL 8.0（InnoDB / utf8mb4） | — |
| 缓存 | Redis 7.x | — |
| 鉴权 | Spring Security + JWT | 6.x |
| 状态机 | 自定义状态机（UdiStateMachine，非第三方库） | — |
| 定时任务 | XXL-Job | 2.4.x |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router + Axios + ECharts | 见 [ivd-web/package.json](ivd-web/package.json) |

**禁止引入**（一期明确排除）：Kafka、Camunda、APISIX、PostgreSQL、K8s、Gradle、后端 TypeScript。禁止的组件不要因为“更时髦”而引入。

## 仓库结构

```
ivd-app/                       Maven 多模块后端（根 pom: com.zlth.ivd, packaging=pom）
├── ivd-common/                共享内核：枚举、工具类、异常、DTO、GlobalExceptionHandler（无依赖，被所有模块依赖）
├── ivd-catalog/                M01 试剂目录 + 五码映射
├── ivd-license/                M03 证照管理
├── ivd-procurement/            M04 采购 + 申领 + 缺货单 + 验收
├── ivd-inventory/               M06 三级库 + FEFO
├── ivd-consumption/            M08 上机与消耗
├── ivd-settlement/              X2 结算（申领结算 + 领用结算）
├── ivd-finance/                M09 日清月结
├── ivd-analytics/               S1 效益分析
├── ivd-rules/                  S2 统一规则引擎
├── ivd-audit/                  S3 批次感知自动审核
├── ivd-udi/                    X1 UDI 追溯引擎（贯穿全流程）
├── ivd-integration/             LIS/HIS 接口适配
└── ivd-server/                 唯一可执行入口（Spring Boot 启动类 + application.yml），打包产物为 ivd-server-1.0.0-mvp.jar

ivd-web/                       前端 Vue3 + Vite 工程
└── src/
    ├── api/                   按模块拆分的 axios 请求封装（catalog.js / license.js / procurement.js / inventory.js / consumption.js / settlement.js / finance.js / analytics.js / audit.js / rules.js / auth.js）
    ├── views/                 页面级组件，目录与后端模块一一对应（catalog/ license/ procurement/ inventory/ consumption/ settlement/ finance/ analytics/ audit/ dashboard/ trace/ layout/ login/）
    ├── components/ stores/ router/ utils/

deploy/                        docker-compose.yml + nginx + mysql 初始化脚本（部署用，非开发日常）
```

**模块依赖方向**（只能向下依赖，不允许循环依赖，跨模块调用必须走 Service 接口，禁止跨模块直接查 Mapper）：

```
ivd-common
  └─ ivd-catalog ─ ivd-license ─ ivd-procurement ─ ivd-inventory ─ ivd-consumption
                                                                       ├─ ivd-settlement ─ ivd-finance ─ ivd-analytics
                                                                       └─ ivd-audit ←─ ivd-rules
```

## 构建 / 运行

> Windows 环境，本机 Maven **未加入 PATH**，必须用绝对路径调用。

- Maven 路径：`C:\Users\Admin\apache-maven-3.9.6\bin\mvn.cmd`
- MySQL 客户端：`C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe`（数据库账号 `root/123456789`，库名 `ivd`）
- Flyway 已在 `application.yml` 中禁用（`spring.flyway.enabled=false`），表结构需手动执行 `db/migration` 下的 SQL

### 重新构建后端 jar（修改任意 Java 源码后必须执行）

```powershell
cd D:\IVD\ivd-app
& "C:\Users\Admin\apache-maven-3.9.6\bin\mvn.cmd" clean package -DskipTests -pl ivd-server -am -o
```

`-o` 为离线模式，使用本地 `~/.m2/repository` 缓存；`-pl ivd-server -am` 只构建 ivd-server 及其依赖模块，比全量构建快。

### 启动 / 停止

- 一键脚本：[start.bat](start.bat) / [stop.bat](stop.bat)（同时管理前后端）
- 单独后端：[start-backend.bat](start-backend.bat)（`java -jar ivd-server-1.0.0-mvp.jar`，端口 8080，独立窗口运行，**看不到控制台日志**）
- 单独前端：[start-frontend.bat](start-frontend.bat)（Vite dev server）
- 数据库初始化：[init-ivd-database.bat](init-ivd-database.bat)

### 调试技巧

- `start-backend.bat` 在独立窗口运行，agent 终端看不到 stdout。若需要看真实异常堆栈（而非被 `GlobalExceptionHandler` 包装后的通用 500），应在当前终端**前台**直接运行 `java -jar ...`，触发请求后读取 stdout。
- 判断 jar 是否过期：对比 jar 文件 `LastWriteTime` 与相关模块 `src/**/*.java` 的 `LastWriteTime`，源码更新时间晚于 jar 说明需要重新打包。
- 重启前先 kill 掉占用 8080 端口的旧 java 进程（`Get-NetTCPConnection -LocalPort 8080` 找 PID，`Stop-Process -Force`）。

## 代码规范要点

### 后端

- 分层：`controller`（薄层，只做参数校验+调用 Service）→ `service`/`service.impl` → `mapper`（MyBatis-Plus）→ `entity`。另有 `dto`（入参）、`vo`（出参，**与 Request DTO 严格分开，不可复用**）、`enums`、`config`、`event`。
- 包名：`com.zlth.ivd.{module}`。
- Service 实现默认加 `@Transactional(rollbackFor = Exception.class)`；跨模块只能注入其他模块的 **Service 接口**，禁止直接注入其他模块的 Mapper。
- 状态变更通过 Spring `ApplicationEvent` 发布解耦；所有状态转移必须在同一事务内完成“更新状态 + 写不可变日志”，转移前调用 `validateTransition` 校验合法性。
- 金额用 `BigDecimal`（`DECIMAL(12,2)`），禁止 `float`/`double`。
- 统一响应包装 `Result<T>`；分页统一 `data.{total,page,pageSize,records}`；错误码范围：`40001-40099` 业务校验、`40100` 未认证、`40300` 无权限、`40400` 不存在、`40900` 冲突、`50000` 服务器内部错误。
- URL 规范：`GET /api/v1/{resource}` 列表、`GET /{id}` 详情、`POST` 新增、`PUT /{id}` 全量更新、`PATCH /{id}` 状态类部分更新、`POST /{id}/approve|reject|reverse` 子资源动作。

### 数据库

- InnoDB + utf8mb4_unicode_ci；主键 `BIGINT AUTO_INCREMENT`（实际 Schema 中存在部分表主键无 AUTO_INCREMENT 标记为"雪花ID"的历史遗留，但 Java 代码中未使用 Snowflake 生成逻辑，所有新增表统一用 AUTO_INCREMENT）；时间用 `DATETIME(3)`；禁止 MySQL 私有扩展函数（如 `GROUP_CONCAT`）。
- 字段蛇形命名，枚举用大写值的 `ENUM` 类型；金额 `DECIMAL(12,2)`，数量 `DECIMAL(10,2)`；所有业务主表必须有 `created_at`/`updated_at`。
- Flyway 脚本命名 `V{YYYYMMDDNNN}__{description}.sql`（NNN 为三位序号），**已执行的脚本不可修改**，只能新增。

### 前端

- 目录按模块对应后端划分（见上方结构）；组件文件名 PascalCase，注册名 kebab-case；弹窗组件以 `Modal` 结尾，Tab 面板以 `Panel` 结尾。
- Pinia store 命名 `use{Xxx}Store`，按模块拆分，不建全局大 store。
- API 封装统一走 `src/api/*.js`，使用 `@/utils/request`（axios 封装）；**新增/排查接口时必须以 `src/api/*.js` 中真实定义的 URL 为准，不要凭猜测拼接路径**（猜测的路径可能命中 `/{resource}/{id}` 之类的路径变量导致类型转换异常，返回误导性的 500 而非 404）。
- Toast 用 `ElMessage.success`/`ElMessage.error`；表格操作列用 `<el-button link>`；库存/审批/影响等级等状态用统一色值（🟢🟡🔴⚪，见开发规范手册 6.5 节）。
- ECharts 统一配色 `['#5470C6','#91CC75','#FAC858','#EE6666','#73C0DE']`。

## 测试

> ⚠️ 以下为**目标规划**，当前实际覆盖远未达标：仅 `ivd-catalog` 模块有 2 个测试文件（均因 ApplicationContext 加载失败），其余 13 个模块零测试。

- 后端单测：JUnit 5 + Mockito（**目标** Service 层覆盖率 ≥ 80%）；DAO 层（**目标** `@MybatisPlusTest` + H2）；集成测试（**目标** `@SpringBootTest` + Testcontainers MySQL + Redis），核心链路（UDI 状态机全路径、申领→审核→结算链路）**目标** 100% 覆盖。
- Controller 层（**目标** MockMvc 验证参数校验、返回格式、错误码）。
- 一期不做 E2E，靠手工验收替代。
- **测试修复记录（2026-07-13）**：`application-test.yml` 缺少 `ivd.jwt.*` 三项配置、`ivd-common/pom.xml` 缺少 `jjwt-impl`/`jjwt-jackson` runtime 依赖、`schema-h2.sql` 缺少 `distributor` 列、测试 DTO 实例缺少必填字段—均已修复，`mvn test -pl ivd-catalog -am` 全部通过。

## Git 工作流摘要

- 分支：`main`（生产，打 tag）← `release/vX.X` ← `develop` ← `feature/{模块}-{描述}` / `bugfix/{描述}`；生产紧急修复用 `hotfix/v{版本}-{描述}`，从 `main` 拉出，修复后必须同时合回 `main` 和 `develop`。
- Commit message 类型前缀：`feat` / `fix` / 其他常规 Conventional Commits 类型，格式 `feat(settlement): 实现申领结算防重锁`，作用域用模块名（如 `settlement`、`audit`、`catalog`）。
- `feature/*`、`bugfix/*` 走 Squash Merge，1 人 review；`hotfix/*` 需 2 人 review。
- 完整规则见开发规范手册第 15 章。

## 已知坑 / 排查经验

1. **改了后端 Java 源码后一定要重新 `mvn package` 打包**，否则 `start-backend.bat` 运行的还是旧 jar，导致新接口出现莫名 500/404。
2. `GlobalExceptionHandler`（`ivd-common`）把所有未捕获异常（含参数类型转换失败、路由未匹配）统一包装成 `HTTP 500 + {"code":50000,...}`，前端只能看到笼统的 "Request failed with status code 500"。排查真实原因必须前台运行 jar 查看 stdout 堆栈。
3. 排查接口问题时只测试 `src/api/*.js` 中真实存在的 URL，不要猜测拼接路径。
4. Windows 环境下 Maven/MySQL 客户端均未加入系统 PATH，调用需用绝对路径（见上方“构建/运行”）。

## 给 AI 助手的提示

- 修改后端代码后，若需要验证效果，记得走完整的“打包 → kill 旧进程 → 重启 → 验证端口/接口”流程，不要只改代码就认为已生效。
- 新增后端接口/字段/枚举时，先查 PRD 和开发规范手册的“枚举值全集”“单号格式全集”附录，保持与既有命名/规则一致，不要自创新格式。
- 跨模块新增依赖时检查模块依赖方向图，禁止反向依赖或循环依赖。
- 涉及金额、批次、状态机的代码改动要格外谨慎（审计要求 APPEND-ONLY 日志、事务原子性），不要为了图简单而绕过状态校验或日志写入。
