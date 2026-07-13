# IVD 智慧实验室管理系统 — 开发规范手册

> **版本**: V1.0-DEV
> **日期**: 2026-06-30
> **依赖**: 《IVD 智慧实验室管理系统_一期MVP_PRD》V1.3
> **密级**: 内部
> **受众**: 后端开发 / 前端开发 / DBA / 测试 / DevOps
>
> 本文档是从 PRD 中提取、固化的开发标准。任何偏离本规范的代码，必须在 code review 中说明理由。

---

## 目录

- [1. 项目架构与模块组织](#1-项目架构与模块组织)
- [2. 技术栈定稿](#2-技术栈定稿)
- [3. 工程结构规范](#3-工程结构规范)
- [4. 数据库设计规范](#4-数据库设计规范)
- [5. 后端代码规范](#5-后端代码规范)
- [6. 前端代码规范](#6-前端代码规范)
- [7. API 设计规范](#7-api-设计规范)
- [8. 状态机实现规范](#8-状态机实现规范)
- [9. Redis 使用规范](#9-redis-使用规范)
- [10. 定时任务规范（XXL-Job）](#10-定时任务规范xxl-job)
- [11. 测试规范](#11-测试规范)
- [12. 错误处理与日志规范](#12-错误处理与日志规范)
- [13. 安全合规规范](#13-安全合规规范)
- [14. 性能标准](#14-性能标准)
- [15. Git 工作流](#15-git-工作流)
  - [15.1 版本号规范](#151-版本号规范semver--医院标识)
  - [15.2 分支体系](#152-分支体系)
  - [15.3 分支命名规范](#153-分支命名规范)
  - [15.4 环境与分支对应](#154-环境与分支对应)
  - [15.5 Commit Message 规范](#155-commit-message-规范)
  - [15.6 Release 发布流程](#156-release-发布流程7-步-sop)
  - [15.7 Hotfix 紧急修复 SOP](#157-hotfix-紧急修复-sop)
  - [15.8 分支保护规则](#158-分支保护规则)
  - [15.9 迭代节奏](#159-迭代节奏)
  - [15.10 医院定制版管理](#1510-医院定制版管理)
  - [15.11 多版本并存策略](#1511-多版本并存策略)
  - [15.12 CI 检查项](#1512-ci-检查项)
- [16. Code Review 清单](#16-code-review-清单)
- [17. 代码注释与文件组织规范](#17-代码注释与文件组织规范)
- [附录 A：PRD 中定义的枚举值全集](#附录-aprd-中定义的枚举值全集)
- [附录 B：PRD 中定义的单号格式全集](#附录-bprd-中定义的单号格式全集)

---

## 1. 项目架构与模块组织

### 1.1 一期架构（单体应用）

```
ivd-app (单体 Spring Boot)
├── ivd-catalog        — M01 试剂目录 + 五码映射
├── ivd-license        — M03 证照管理
├── ivd-procurement    — M04 采购管理 + 申领 + 缺货单 + 验收
├── ivd-inventory      — M06 三级库 + FEFO
├── ivd-consumption    — M08 上机与消耗
├── ivd-settlement     — X2 第一层结算（申领结算 + 领用结算）
├── ivd-finance        — M09 日清月结
├── ivd-analytics      — S1 效益分析
├── ivd-rules          — S2 统一规则引擎
├── ivd-audit          — S3 批次感知自动审核
├── ivd-udi            — X1 UDI 追溯引擎（贯穿）
├── ivd-common         — 共享内核（枚举、工具类、异常、DTO）
└── ivd-integration    — LIS/HIS 接口适配
```

### 1.2 模块依赖方向

```
ivd-common（无依赖，被所有模块依赖）
    ↑
ivd-catalog ← ivd-license ← ivd-procurement ← ivd-inventory
                                                  ↑
                                          ivd-consumption
                                                  ↑
                    ┌─────────────────────────────┤
                    ↓                             ↓
            ivd-settlement (X2)           ivd-audit (S3)
                    ↓
            ivd-finance (M09) ← ivd-analytics (S1)
                    ↑
            ivd-rules (S2) ──→ ivd-audit
```

**规则**：
- 模块间只能向下依赖（不允许循环依赖）
- 跨模块调用走 Service 接口，不直接跨模块查 DAO
- 模块内部 package 私有的类不对外暴露

---

## 2. 技术栈定稿

### 2.1 后端（一期 MVP 定稿，不可随意变更）

| 层     | 组件                    | 版本                     | 锁定原因                                            |
| ----- | --------------------- | ---------------------- | ----------------------------------------------- |
| 语言    | Java                  | JDK 17 (LTS)           | 信创中间件兼容基线                                       |
| 框架    | Spring Boot           | 3.2.x                  | 医疗行业主流                                          |
| 构建    | Maven                 | 3.9.x                  | 国产化环境构建稳定（弃用 Gradle）                            |
| ORM   | MyBatis-Plus          | 3.5.x                  | SQL 可控性强，动态查询灵活（弃用 JPA）                         |
| 数据库   | MySQL                 | 8.0 (InnoDB / utf8mb4) | 信创可平替达梦/OceanBase                               |
| 连接池   | HikariCP              | Spring Boot 内置         | 默认，性能最优                                         |
| 缓存    | Redis                 | 7.x                    | 批次关联 <50ms 硬要求                                  |
| 消息    | 无（轻量队列）               | —                      | Spring ApplicationEvent + Redis Stream 替代 Kafka |
| 对象存储  | MinIO                 | RELEASE.2024+          | 证照 PDF 存储                                       |
| 任务调度  | XXL-Job               | 2.4.x                  | 日清月结 / FEFO 重算 / 证照预警                           |
| 数据库迁移 | Flyway                | 9.x（MySQL 驱动）          | 数据库版本管理                                         |
| 鉴权    | Spring Security + JWT | 6.x                    | RBAC + IP 白名单                                   |
| 工作流   | Spring StateMachine   | 3.x                    | 轻量审批流（不用 Camunda）                               |
| 部署    | Docker Compose        | v2                     | 单机/小集群，不上 K8s                                   |

### 2.2 前端（一期 MVP 定稿）

| 层        | 组件           | 版本             |
| -------- | ------------ | -------------- |
| 框架       | Vue 3        | 3.4            |
| UI 库     | Element Plus | 2.x            |
| 图表       | ECharts      | 5.x            |
| 构建       | Vite         | 5.x            |
| 状态管理     | Pinia        | 2.x            |
| 路由       | Vue Router   | 4.x            |
| HTTP 客户端 | Axios        | 1.x            |
| 表格增强     | vxe-table    | 4.x（可选，复杂表格场景） |

### 2.3 禁止引入的组件（一期不做）

| 禁止项              | 替代方案                                   |
| ---------------- | -------------------------------------- |
| ❌ Kafka          | Spring ApplicationEvent + Redis Stream |
| ❌ Camunda        | Spring StateMachine                    |
| ❌ APISIX         | Spring Cloud Gateway（预留）               |
| ❌ PostgreSQL     | MySQL 8.0                              |
| ❌ K8s            | Docker Compose v2                      |
| ❌ Gradle         | Maven                                  |
| ❌ TypeScript（后端） | Java                                   |

---

## 3. 工程结构规范

### 3.1 Maven 模块命名

```
com.zlth.ivd.{module}
```

模块级 artifactId：
- `ivd-common` — 共享内核
- `ivd-catalog` — M01 试剂目录
- `ivd-license` — M03 证照管理
- `ivd-procurement` — M04 采购+申领+验收
- `ivd-inventory` — M06 三级库
- `ivd-consumption` — M08 上机+消耗
- `ivd-settlement` — X2 结算
- `ivd-finance` — M09 日清月结
- `ivd-analytics` — S1 效益分析
- `ivd-rules` — S2 规则引擎
- `ivd-audit` — S3 自动审核
- `ivd-udi` — X1 UDI 追溯

### 3.2 模块内部 Package 结构（统一模板）

```
com.zlth.ivd.{module}
├── controller      — REST 控制器（薄层，只做参数校验+调用 Service）
├── service         — 业务接口
│   └── impl        — 业务实现
├── mapper          — MyBatis-Plus Mapper 接口
├── entity          — 数据库实体（PO）
├── dto             — 数据传输对象（入参/出参）
├── vo              — 视图对象（前端展示专用）
├── enums           — 模块私有枚举
├── config          — 模块配置类
└── event           — Spring 事件（状态机事件监听）
```

### 3.3 文件命名规范

| 类型 | 命名模式 | 示例 |
|------|---------|------|
| Controller | `{Entity}Controller` | `ReagentCatalogController` |
| Service 接口 | `{Entity}Service` | `ReagentCatalogService` |
| Service 实现 | `{Entity}ServiceImpl` | `ReagentCatalogServiceImpl` |
| Mapper | `{Entity}Mapper` | `ReagentCatalogMapper` |
| Entity | `{TableName}` (驼峰) | `ReagentCatalog` |
| DTO (Request) | `{Action}{Entity}Request` | `CreateReagentRequest` |
| DTO (Response) | `{Entity}Response` / `{Entity}VO` | `ReagentCatalogVO` |
| 枚举 | `{Prefix}{Enum}` | `MaterialTypeEnum` |

---

## 4. 数据库设计规范

### 4.1 通用约定

- 引擎：**InnoDB**
- 字符集：**utf8mb4**
- 排序规则：**utf8mb4_unicode_ci**
- 主键：**BIGINT AUTO_INCREMENT**（分布式场景改用雪花 ID）
- 时间字段：**DATETIME(3)**（毫秒精度）
- **禁止使用 MySQL 私有扩展函数**（如 `GROUP_CONCAT`）——信创可切换
- 所有表必须加 `COMMENT`

### 4.2 表命名规范

| 类别 | 前缀/模式 | 示例 |
|------|----------|------|
| 业务主表 | `{domain}_{entity}` | `reagent_catalog`, `purchase_order`, `settlement_record` |
| 日志/流水表 | `{domain}_log` | `udi_status_log`, `catalog_approval_log`, `settlement_reversal_log` |
| 关联中间表 | `{a}_{b}_rel` | `reagent_supplier_rel` |
| 预留空表 | `{domain}`（加 `TODO: Phase 2` 注释） | `qc_result`, `poct_device` |

### 4.3 字段命名规范

- **蛇形命名**（snake_case）：`material_type`, `settlement_status`, `created_at`
- 布尔值：`TINYINT(1) NOT NULL DEFAULT 0`
- 枚举：MySQL `ENUM` 类型（值用大写），`NOT NULL`，加 `COMMENT`
- 金额：`DECIMAL(12,2)`（12 位总长，2 位小数）
- 数量：`DECIMAL(10,2)`（支持小数数量，如 3.5 盒）
- ID 外键：`{entity}_id BIGINT`
- 时间戳：`_at` 后缀（`created_at`, `updated_at`, `settled_at`, `reversed_at`）
- 审计字段：`_by` 后缀 + `INT`（`submitted_by`, `approved_by`）

### 4.4 必建标准字段

所有业务主表必须包含：

```sql
created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
```

审批/状态变更表额外包含：

```sql
submitted_by INT COMMENT '提交人ID',
submitted_at DATETIME(3),
approved_by INT COMMENT '审批人ID',
approved_at DATETIME(3)
```

### 4.5 索引规范

- 索引命名：`idx_{table_abbr}_{column}` 或 `idx_{table_abbr}_{col1}_{col2}`
- 联合索引：把等值查询列放前面，范围查询列放后面
- 外键列必须有索引
- 日志/流水表按时间 RANGE 分区（按月），查询必须带分区键
- `udi_status_log` 必须 RANGE 分区按月

### 4.6 Flyway 迁移脚本

- 命名：`V{YYYYMMDDHHMM}__{description}.sql`
- 示例：`V2026063001__init_reagent_catalog.sql`
- **不可修改已执行的迁移脚本**——只能加新脚本
- 预留二期表在脚本中注释 `-- TODO: Phase 2`

### 4.7 枚举值对照（必须与 PRD 严格一致）

详见 [附录 A](#附录-aprd-中定义的枚举值全集)。

---

## 5. 后端代码规范

### 5.1 Controller 层

```java
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Slf4j
public class ReagentCatalogController {

    private final ReagentCatalogService catalogService;

    @GetMapping
    public Result<PageVO<ReagentCatalogVO>> list(ReagentCatalogQuery query) {
        // 薄层：只做参数校验和调用 Service
        return Result.success(catalogService.pageList(query));
    }

    @PostMapping
    public Result<ReagentCatalogVO> create(@Valid @RequestBody CreateReagentRequest request) {
        return Result.success(catalogService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ReagentCatalogVO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReagentRequest request) {
        return Result.success(catalogService.update(id, request));
    }
}
```

**规则**：
- REST URL 用复数名词，kebab-case
- `@Valid` / `@Validated` 做参数校验
- 统一返回 `Result<T>` 包装
- Controller 不做业务逻辑——最多做参数转换
- 所有接口加入 `@Slf4j` 日志

### 5.2 Service 层

```java
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class ReagentCatalogServiceImpl implements ReagentCatalogService {

    // Service 之间通过接口依赖，不直接注入 Mapper
    private final ReagentCatalogMapper catalogMapper;
    private final FiveCodeMappingService mappingService; // ← 跨模块走接口

    @Override
    public ReagentCatalogVO create(CreateReagentRequest request) {
        // 1. 业务校验
        validateDuplicateCode(request.getHisCode());
        // 2. 构建实体
        ReagentCatalog entity = buildEntity(request);
        // 3. 持久化
        catalogMapper.insert(entity);
        // 4. 发布事件（状态变更）
        eventPublisher.publishEvent(new CatalogCreatedEvent(entity));
        // 5. 返回
        return toVO(entity);
    }
}
```

**规则**：
- `@Transactional(rollbackFor = Exception.class)` 默认加在 Service 实现上
- 跨模块调用只能注入 Service 接口，**不可直接注入其他模块的 Mapper**
- 状态变更通过 Spring `ApplicationEvent` 发布，解耦下游
- 复杂业务拆成私有方法，方法名见名知意

### 5.3 MyBatis-Plus Mapper

```java
@Mapper
public interface ReagentCatalogMapper extends BaseMapper<ReagentCatalog> {

    /**
     * 自定义分页查询（多条件组合）
     * 返回 IPage，MyBatis-Plus 自动处理分页
     */
    IPage<ReagentCatalogVO> pageList(
            Page<ReagentCatalog> page,
            @Param("query") ReagentCatalogQuery query);

    /**
     * 按审批状态查询待办列表
     */
    List<ReagentCatalog> findByStatus(
            @Param("status") String status,
            @Param("limit") int limit);
}
```

**规则**：
- Mapper XML 中的 SQL 必须使用 MySQL 标准方言
- 复杂查询写在 XML 中，不使用 MyBatis-Plus 的 LambdaQueryWrapper 写复杂条件
- 分页统一使用 MyBatis-Plus 的 `IPage`
- 所有动态 SQL 注意 SQL 注入防护（MyBatis `#{}` 预编译）

### 5.4 Entity 定义

```java
@Data
@TableName("reagent_catalog")
public class ReagentCatalog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String hisCode;                       // 院内编码
    private String genericName;                   // 物料名称
    private MaterialTypeEnum materialType;        // ★ 物料类型（枚举）
    private String udiDi;                         // UDI-DI
    private String registrationCertNo;            // 注册证号

    @TableField("tests_per_kit")
    private Integer testsPerKit;                  // 标称测试数

    @TableField("price_per_unit")
    private BigDecimal pricePerUnit;              // 单价

    private ApprovalStatusEnum approvalStatus;    // 审批状态（枚举）
    private DeviceBindModeEnum deviceBindMode;    // 设备关联策略（枚举）

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**规则**：
- Entity 字段命名：Java 驼峰 × DB 蛇形（MyBatis-Plus 自动转换）
- 枚举类型在 Entity 中使用 `{EnumType}`，MyBatis-Plus 配置 TypeHandler 自动转换
- `BigDecimal` 用于金额，禁止 `float`/`double`
- `@TableField` 仅在字段名与列名不一致时使用

### 5.5 DTO 与 VO 分离

```
Request DTO (入参) → Controller 接收
    ↓
Entity (PO) → Service 操作数据库
    ↓
Response VO (出参) → Controller 返回前端
```

**规则**：
- **Request DTO 和 Response VO 必须分开定义**——不可复用
- Request 中用 `@NotBlank` / `@NotNull` / `@Positive` 做校验
- VO 中不包含数据库内部字段（如 `is_deleted`）
- 敏感字段（如患者 ID 哈希）在 VO 中明确标注 `@JsonProperty("patient_id_hash")`

---

## 6. 前端代码规范

### 6.1 Vue 组件组织

```
src/
├── views/                 — 页面级组件（对应路由）
│   ├── catalog/           — M01 试剂目录
│   │   ├── CatalogList.vue
│   │   ├── CatalogEdit.vue
│   │   └── UdiBacklogPanel.vue  — UDI 待补全面板
│   ├── license/           — M03 证照管理
│   ├── procurement/       — M04 采购管理
│   ├── inventory/         — M06 三级库
│   ├── consumption/       — M08 上机消耗
│   ├── settlement/        — X2 结算管理
│   ├── finance/           — M09 日清月结
│   ├── analytics/         — S1 效益分析
│   ├── audit/             — S3 自动审核
│   └── dashboard/         — 主任驾驶舱
├── components/            — 通用组件
│   ├── common/            — 基础组件（Button, Modal 封装）
│   ├── business/          — 业务组件（物料选择器, 批次选择器）
│   └── charts/            — 图表组件
├── stores/                — Pinia 状态管理
├── api/                   — API 请求封装
├── router/                — 路由配置
└── utils/                 — 工具函数
```

### 6.2 组件命名规范

- 文件名：**PascalCase**（`CatalogList.vue`, `SettlementRecord.vue`）
- 组件注册名：**kebab-case**（`<catalog-list />`）
- 弹窗组件：以 `Modal` 结尾（`UdiFillModal.vue`, `MixedAcceptModal.vue`）
- Tab 面板：以 `Panel` 结尾（`UdiBacklogPanel.vue`）

### 6.3 Pinia Store 命名

```javascript
// stores/catalog.js
export const useCatalogStore = defineStore('catalog', () => {
    const list = ref([])
    const loading = ref(false)

    async function fetchList(query) { /* ... */ }

    return { list, loading, fetchList }
})
```

- Store 命名：`use{Xxx}Store`
- 按模块拆分 Store，不建全局大 Store

### 6.4 API 封装

```javascript
// api/catalog.js
import request from '@/utils/request'

export function getCatalogList(params) {
    return request({ url: '/api/v1/catalog', method: 'get', params })
}

export function createReagent(data) {
    return request({ url: '/api/v1/catalog', method: 'post', data })
}
```

### 6.5 UI 开发规则

- 表格操作列：统一使用 Element Plus `<el-table-column>` + `<el-button link>` 小号文本按钮
- KPI 统计卡：4 列 `<el-row :gutter="16">` 内嵌 `<el-statistic>`
- 弹窗（Modal）：宽度标准——简单表单 520px / 明细查看 960px / 复杂表单 1040px
- 审批状态显示：🟢 已批准 / 🟡 待审批（**脉动动画**）/ ⚪ 草稿 / 🔴 已驳回
- 影响等级显示：🔴 阻断入库 / 🟡 预警 / 🟢 正常
- 库存颜色：≥20 = 绿 / 0 < 库存 < 20 = 黄 + 预警标签 / 0 = 红 + 缺货标签
- 表单必填项：红色 `*` 标注
- Toast / Message：操作成功用 `ElMessage.success`，失败用 `ElMessage.error`

### 6.6 ECharts 集成规范

- 统一使用 `echarts` 5.x，不引入其他图表库
- 图表组件封装为独立组件，接收 `options` prop
- 饼图/柱状图/折线图统一配色：`['#5470C6', '#91CC75', '#FAC858', '#EE6666', '#73C0DE']`
- 下钻分析图表支持点击事件跳转详情

---

## 7. API 设计规范

### 7.1 URL 设计

```
GET    /api/v1/{resource}           — 分页列表（Query Params 传筛选条件）
GET    /api/v1/{resource}/{id}      — 详情
POST   /api/v1/{resource}           — 新增
PUT    /api/v1/{resource}/{id}      — 全量更新
PATCH  /api/v1/{resource}/{id}      — 部分更新（如审批、状态变更）
DELETE /api/v1/{resource}/{id}      — 软删除

POST   /api/v1/{resource}/batch     — 批量操作
POST   /api/v1/{resource}/{id}/approve  — 子资源操作（审批通过）
POST   /api/v1/{resource}/{id}/reject   — 子资源操作（驳回）
POST   /api/v1/{resource}/{id}/reverse  — 子资源操作（冲销）
```

### 7.2 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "timestamp": "2026-06-30T10:00:00"
}
```

分页响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 150,
    "page": 1,
    "pageSize": 10,
    "records": [ ]
  }
}
```

错误响应：

```json
{
  "code": 40001,
  "message": "该物料已在采购订单中使用，无法删除",
  "detail": "试剂 ID=42 与采购订单 PO-20260630-0001 关联",
  "timestamp": "2026-06-30T10:00:00"
}
```

### 7.3 错误码规范

| 范围 | 含义 |
|------|------|
| 200 | 成功 |
| 40001-40099 | 业务校验失败（参数非法、数据冲突） |
| 40100 | 未认证 |
| 40300 | 无权限 |
| 40400 | 资源不存在 |
| 40900 | 数据冲突（如：重复结算、状态不允许） |
| 50000 | 服务器内部错误 |

### 7.4 API 鉴权

- LIS/HIS → 本系统：JWT + IP 白名单
- 前端 → 本系统：JWT（登录签发，过期刷新）
- 同步接口（审核 A）：超时 3s 兜底返回 `AUTO_RELEASE`
- 所有 API 调用必须记录审计日志（5W：谁、何时、何接口、何参数、何结果）

---

## 8. 状态机实现规范

### 8.1 状态机类型与实现方案

| 状态机 | 用途 | 技术实现 |
|--------|------|---------|
| UDI 状态机 | 批次生命周期（11 状态） | Spring ApplicationEvent + Redis Stream |
| 审批状态机 | 目录变更（4 状态） | Spring StateMachine |
| 结算状态机 | X2 settlement_status（3 状态） | Spring StateMachine |
| 采购状态机 | PO 状态（6 状态） | Spring StateMachine |
| 申领状态机 | 申领单（8 状态） | Spring StateMachine |
| 证照状态机 | 证照生命周期（4 状态） | Spring StateMachine |
| 审核决策 | S3 规则匹配（4 动作） | 规则引擎（自研） |

### 8.2 状态转移实现模板

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class UdiStateMachine {

    private final ApplicationEventPublisher eventPublisher;
    private final UdiStatusLogMapper logMapper;

    @Transactional(rollbackFor = Exception.class)
    public void transition(String batchNo, UdiStatus from, UdiStatus to, String operator) {
        // 1. 合法性校验（对照状态转移矩阵）
        validateTransition(from, to);

        // 2. 更新批次状态
        reagentBatchMapper.updateStatus(batchNo, to);

        // 3. 写入不可变日志
        UdiStatusLog log = UdiStatusLog.builder()
                .batchNo(batchNo)
                .fromStatus(from)
                .toStatus(to)
                .operator(operator)
                .occurredAt(LocalDateTime.now())
                .build();
        logMapper.insert(log);

        // 4. 发布事件（异步通知下游）
        eventPublisher.publishEvent(new UdiStatusChangedEvent(batchNo, from, to, operator));
    }
}
```

**规则**：
- 所有状态转移 **必须在同一个事务内** 完成（更新状态 + 写日志）
- 转移前必须 `validateTransition`——非法转移抛 `IllegalStateTransitionException`
- 状态日志表（`udi_status_log`）**APPEND-ONLY**，不可修改、不可删除
- 事件发布后异步处理（用 `@Async` 或 `@TransactionalEventListener`）

---

## 9. Redis 使用规范

### 9.1 Key 命名规范

```
ivd:{module}:{entity}:{id}
```

示例：

| Key | 用途 | 类型 | TTL |
|-----|------|------|-----|
| `ivd:device_batches:{device_id}` | 设备→活跃批次映射 | Sorted Set | 无（持久） |
| `ivd:batch:{batch_no}` | 批次详情缓存 | Hash | 1h |
| `ivd:batch_avg_cost:{batch_no}` | 批次移动加权平均成本 | String | 实时（入库时更新） |
| `ivd:license_expire` | 证照到期预警队列 | Sorted Set | 无（持久） |
| `ivd:settlement_lock:{source_id}` | 结算防重锁 | String | 30s |

### 9.2 批次映射缓存（核心命脉）

```
Key:   ivd:device_batches:{device_id}
Type:  Sorted Set
Member: {batch_no}:{load_time_epoch}:{unload_time_epoch}
Score: load_time_epoch (毫秒时间戳)
```

**操作规范**：
- 上机时 `ZADD key load_time_epoch "{batch_no}:{load_time_epoch}:0"`
- 退机时 `ZADD key load_time_epoch "{batch_no}:{load_time_epoch}:{unload_time_epoch}"`（覆盖更新 unload_time）
- 审核时 `ZRANGEBYSCORE key test_time_epoch test_time_epoch LIMIT 0 1` —— 查测试时间落在哪个上机区间
- **绝不能每次审核查数据库**——性能要求 <50ms

### 9.3 Redis 使用禁止项

- ❌ 禁止存大对象（单 value > 1MB）
- ❌ 禁止 `KEYS *` 命令（阻塞）
- ❌ 禁止无 TTL 的缓存（防止 OOM）
- ❌ 禁止在 Redis 做复杂计算（放到应用层）

---

## 10. 定时任务规范（XXL-Job）

### 10.1 任务清单

| 任务名称 | 执行频率 | 说明 |
|---------|---------|------|
| `dailySettlement` | 每日 23:00 | M09 日结 |
| `monthlySettlement` | 每月 1 日 02:00 | M09 月结 |
| `fefoRecalculate` | 每日 03:00 | FEFO 优先级重算 |
| `licenseExpireCheck` | 每日 08:00 | 证照效期检查 + 分级预警推送 |
| `avgCostRecalculate` | 每次验收入库后 + 每日 22:00 | 移动加权平均成本重算 |
| `settlementRetryPending` | 每日 23:30 | 兜底批量结算（处理即时结算失败记录） |
| `pendingUdiAlert` | 每日 09:00 | PENDING_UDI 超 7 天告警 |

### 10.2 任务编写规范

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DailySettlementJob {

    private final FinanceService financeService;

    @XxlJob("dailySettlement")
    public void execute() {
        String date = LocalDate.now().minusDays(1).toString();
        XxlJobHelper.log("开始日结: {}", date);
        try {
            financeService.dailySettlement(date);
            XxlJobHelper.handleSuccess("日结完成");
        } catch (Exception e) {
            log.error("日结失败", e);
            XxlJobHelper.handleFail("日结失败: " + e.getMessage());
        }
    }
}
```

**规则**：
- 所有定时任务必须有 try-catch
- 调用 `XxlJobHelper.handleSuccess` / `handleFail` 明确返回状态
- 幂等设计：同一任务同一天重复执行不产生脏数据（用日期判断 `settled_at`）

---

## 11. 测试规范

### 11.1 测试分层

| 层级 | 工具 | 覆盖率要求 | 说明 |
|------|------|-----------|------|
| 单元测试 | JUnit 5 + Mockito | Service 层 ≥ 80% | Mock 所有外部依赖 |
| DAO 层测试 | @MybatisPlusTest + H2 | Mapper 方法 ≥ 90% | 内嵌数据库，不连真实 MySQL |
| 集成测试 | @SpringBootTest + Testcontainers (MySQL + Redis) | 核心链路 100% | UDI 状态机 / 审核流水线 / 结算链路 |
| API 测试 | MockMvc | Controller 层 100% | 参数校验 / 返回格式 / 错误码 |
| E2E | 不做（一期） | — | 手工验收替代 |

### 11.2 核心链路测试（必须覆盖）

1. **UDI 状态机全路径**：RECEIVED → IN_STOCK → IN_USE → CONSUMED → PENDING_UDI → IN_STOCK
2. **申领→结算链路（X2）**：申领提交 → 审核发货 → 即时结算 → settlement_record → M09 可见
3. **采购全链路**：缺货单 → PO → 扫码验收 → 入库 → UDI 状态变更 → 补发
4. **审核流水线**：LIS 推送 → 批次关联 → 规则匹配 → 决策返回 → 日志写入
5. **防重结算**：已 SETTLED 记录再次提交结算 → 409
6. **冲销闭环**：冲销 → REVERSED → 源表 REVERSED → 审计日志可查

### 11.3 测试命名

```
{被测方法}_{场景}_{期望结果}
```

示例：
```java
@Test
void settleRequisition_whenAlreadySettled_shouldThrowConflictException() { }

@Test
void transitionUdiStatus_whenIllegalPath_shouldThrowException() { }

@Test
void auditObservation_whenServiceUnavailable_shouldAutoRelease() { }
```

---

## 12. 错误处理与日志规范

### 12.1 异常体系

```
BizException (业务异常，code + message)
├── IllegalStateTransitionException   — 非法状态转移
├── DuplicateSettlementException      — 重复结算 (409)
├── InsufficientStockException        — 库存不足
├── LicenseExpiredException           — 证照过期
├── UdiMissingException               — UDI 缺失
└── ApprovalPendingException          — 审批中冲突
```

**规则**：
- 所有业务异常继承 `BizException`
- 业务异常带 `errorCode`（对应 7.3 错误码表）
- Controller 层用 `@ControllerAdvice` 统一处理异常，返回标准 JSON

### 12.2 日志规范

```java
// INFO: 关键业务节点
log.info("申领结算完成: settlement_no={}, dept={}, amount={}", no, dept, amount);

// WARN: 可恢复异常
log.warn("批次关联失败，兜底取最近批次: device_id={}, time={}", deviceId, time);

// ERROR: 不可恢复异常
log.error("验收入库事务失败: udi={}, po_no={}", udi, poNo, exception);
```

**规则**：
- 使用 Lombok `@Slf4j`
- 日志用占位符 `{}`，不要字符串拼接
- **禁止打印患者明文 ID**——只能打 `patient_id_hash`
- 金额日志打印时保留 2 位小数
- 关键业务操作（状态变更、结算、审核决策）必须 INFO 日志

---

## 13. 安全合规规范

### 13.1 患者隐私保护（PHI）

| 场景 | 措施 |
|------|------|
| 入库存储 | 患者 ID 入库前 **SHA-256 哈希**，不存明文 |
| 日志打印 | 禁止打印患者明文信息 |
| 传输 | TLS 1.2+ |
| 存储加密 | 患者相关列应用层 AES-256 加密 |

### 13.2 访问控制

- RBAC 角色权限矩阵（见 PRD 3.1）
- 高危操作二次确认：冲销结算、删除试剂、冻结证照、切换设备关联策略
- 审批通过才能生效：试剂目录编辑、证照变更

### 13.3 审计追踪

所有以下操作必须写入审计日志（5W 不可篡改）：
- 状态变更（UDI 状态 / 审批状态 / 结算状态 / 证照状态 / PO 状态）
- 数据修改（试剂目录 / 证照 / 规则）
- API 调用（LIS 推送 / HIS 导入 / 批量操作）
- 冲销操作

### 13.4 兜底安全

> ⚠️ **审核服务不可用时，默认 `AUTO_RELEASE` 放行**——不能因本系统故障导致医院停摆。

---

## 14. 性能标准

### 14.1 硬性指标

| 指标 | 目标 | 测量方式 |
|------|------|---------|
| UDI 扫码响应 | P95 < 200ms | 验收场景 API 耗时 |
| 库存查询 | P95 < 500ms | 多维筛选 API 耗时 |
| **审核决策延迟** | **P95 < 500ms** | LIS 同步接口耗时（含 Redis 批次关联） |
| 批次关联查询 | < 50ms | 纯 Redis 操作耗时 |
| 并发扫码 | ≥ 3000 次/小时 × 4h | 压测 |
| 系统可用性 | ≥ 99.5% | 月度统计 |

### 14.2 性能必做

- `device_batch_load` 必须建联合索引 `(device_id, load_time, unload_time)`
- `udi_status_log` 按月 RANGE 分区
- 批次关联 **必须走 Redis Sorted Set**，禁止查数据库
- 列表查询默认分页 10 条，禁止一次返回全量
- 审核接口同步返回，P95 < 500ms

---

## 15. Git 工作流

> 采用 **增强版 Git Flow**，适配医院私有化部署、多版本并存、医疗合规审计三大场景。

---

### 15.1 版本号规范（Semver + 医院标识）

```
v<MAJOR>.<MINOR>.<PATCH>[-<HOSPITAL>]

MAJOR    — 架构重构 / 模块重写（一期→二期、Spring Boot 大版本升级）
MINOR    — 新模块上线 / 核心功能新增（M05 质控上线、审批流重构）
PATCH    — Bug 修复 / 性能优化 / 配置调整
HOSPITAL — 可选，医院定制标识（如 HIS 接口适配、本地化配置）
```

| 版本示例 | 含义 |
|---------|------|
| `v1.0.0` | 一期 MVP 基线版本 |
| `v1.1.0` | 新增 M05 质控管理模块 |
| `v1.1.1` | 修复结算冲销 409 错误码 |
| `v1.1.1-rjyy` | 人民医院定制版（HIS 接口差异化适配） |

**规则**：
- 每合并到 `main` **必须打 tag**，tag 名 = 版本号
- tag 附注写清楚：交付目标医院、新增模块、已知限制
- 医院定制版从对应基线 tag 拉分支，不混入主线

---

### 15.2 分支体系

```
main (生产基线 — 每个 commit 都是一个可交付版本)
│
├── tag: v1.0.0                       ← 一期 MVP 基线
├── tag: v1.1.0                       ← M05 质控上线
├── tag: v1.1.1                       ← 结算 bugfix
├── tag: v1.1.1-rjyy                  ← 人民医院定制
│
├── develop (开发主线 — 所有 feature 的汇合点)
│   ├── feature/m01-catalog-crud      ← 功能开发
│   ├── feature/x2-auto-settlement
│   ├── bugfix/udi-status-transition  ← 非紧急修复
│   └── refactor/audit-engine
│
├── release/v1.0.0                    ← 发布候选分支（冻结 → 测试 → 合并 main）
├── release/v1.1.0
│
├── hotfix/v1.0.1-settlement-duplicate ← 生产紧急修复（从 main 拉）
│
└── hospital/rjyy-v1.1.1              ← 医院定制分支（基于 tag 拉出）
```

---

### 15.3 分支命名规范

```
feature/<模块>-<简短描述>       — 新功能
bugfix/<简短描述>                — 非紧急修复
hotfix/v<版本>-<简短描述>        — 生产紧急修复
release/v<MAJOR>.<MINOR>.<PATCH> — 发布候选
refactor/<模块>-<简短描述>       — 重构（不改变功能）
hospital/<医院缩写>-<基础版本>    — 医院定制版
```

**示例**：
```
feature/m01-catalog-search
feature/x2-settlement-reversal
bugfix/udi-status-illegal-transition
hotfix/v1.0.1-settlement-duplicate
release/v1.1.0
refactor/audit-extract-rule-engine
hospital/rjyy-v1.1.1
```

---

### 15.4 环境与分支对应

```
feature/*  →  develop  →  release/vX.X  →  main (tag)
    │              │              │              │
    └── 本地开发    └── DEV 环境   └── UAT 环境   └── PROD 环境
                  (开发联调)     (验收测试)     (医院现场)
```

| 环境 | 对应分支 | 触发方式 | 数据库 | 用途 |
|------|---------|---------|--------|------|
| **DEV** | `develop` | Push 自动构建 | H2 / 脱敏库 | 前后端联调、每日构建 |
| **TEST** | `release/*` | PR 自动触发 | 脱敏生产样例数据 | 集成测试 + 压力测试 |
| **UAT** | `release/*` | 手动部署 | 真实脱敏数据 | 关键用户验收、回归测试 |
| **PROD** | `main` (tag) | **手动部署** | 真实数据 | 医院现场运行 |

> ⚠️ **PROD 环境禁止自动部署**——所有生产发布必须人工确认，且部署后观察 72 小时。

---

### 15.5 Commit Message 规范

#### 15.5.1 格式

```
<type>(<scope>): <subject>                     ← 50 字符上限

<body>                                          ← 72 字符换行，解释 What & Why

Refs: #<issue-id>                               ← 关联需求/缺陷编号
Reviewed-by: <reviewer>                         ← Code Review 通过者
```

#### 15.5.2 Type 类型

| Type | 用途 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(settlement): 实现申领结算防重锁` |
| `fix` | Bug 修复 | `fix(audit): 修复审核超时未触发兜底放行` |
| `refactor` | 重构（无功能变更） | `refactor(udi): 提取状态转移校验为独立工具类` |
| `docs` | 文档 | `docs(api): 补充结算接口错误码说明` |
| `test` | 测试 | `test(settlement): 补充重复结算集成测试` |
| `chore` | 构建/工具/依赖 | `chore(deps): 升级 MyBatis-Plus 至 3.5.7` |
| `perf` | 性能优化 | `perf(inventory): 优化 FEFO 计算批量查询` |
| `style` | 代码格式（不影响逻辑） | `style(catalog): 统一缩进为 4 空格` |

#### 15.5.3 Scope（模块标识）

```
catalog | license | procurement | inventory | consumption
settlement | finance | analytics | rules | audit | udi
common | integration | deploy | config
```

#### 15.5.4 完整示例

```
feat(settlement): 实现申领结算防重锁机制

通过 Redis 分布式锁 + DB 唯一约束双重保障，防止同一申领单被并发结算。
锁 Key: ivd:settlement_lock:{reqNo}，TTL 30s，结算完成后立即释放。

Refs: #142
Reviewed-by: 李四
```

```
fix(audit): 修复审核服务超时未正确触发 AUTO_RELEASE

当审核规则引擎响应超过 3s 时，原代码抛 TimeoutException 并返回 500，
导致 LIS 端收到错误后重试，形成雪崩。修复后正确返回 AUTO_RELEASE，
与 §13.4 兜底安全策略对齐。

Refs: #207
Reviewed-by: 王五
```

---

### 15.6 Release 发布流程（7 步 SOP）

```
 ┌──────────┐
 │ 第 1 天   │  从 develop 拉 release/v1.1.0 → 冻结新功能
 └────┬─────┘
      ▼
 ┌──────────┐
 │ 第 2 天   │  部署 TEST 环境 → 全量回归测试 + 压力测试
 └────┬─────┘
      ▼
 ┌──────────┐
 │ 第 3-4 天 │  修复 release 分支上的测试缺陷（不新增功能）
 └────┬─────┘
      ▼
 ┌──────────┐
 │ 第 5 天   │  部署 UAT 环境 → 关键用户验收
 └────┬─────┘
      ▼
 ┌──────────┐
 │ 第 6 天   │  release → main (PR, 2 人 approve) → 打 tag v1.1.0
 └────┬─────┘
      ▼
 ┌──────────┐
 │ 第 6 天   │  release → develop (反向合并，带回测试期间的修复)
 └────┬─────┘
      ▼
 ┌──────────┐
 │ 第 7 天   │  医院现场部署 → 监控 72 h → 发布完成 ✅
 └──────────┘
```

**规则**：
- Release 分支创建即**功能冻结**——此后只修缺陷，不新增功能
- Release → main 的 PR **必须 2 人 approve**
- 合并后**立即打 tag**，tag 消息写清交付内容和已知限制
- Release → develop 的反向合并**不可省略**——否则修复丢失
- 医院现场部署后 **72 小时内核心开发保持在线**，随时响应

---

### 15.7 Hotfix 紧急修复 SOP

#### 15.7.1 触发条件（P0 / P1）

| 级别 | 定义 | 响应时间 | 示例 |
|:----:|------|:---:|------|
| **P0** | 系统不可用 / 患者安全风险 | 30 min | 结算全挂、审核全部卡死、数据丢失 |
| **P1** | 核心功能受阻 | 2 h | 证照过期未预警、扫码验收报错 |
| P2 | 非核心功能异常 | 下一个工作日 | 报表数据延迟、UI 显示错位 |

#### 15.7.2 执行流程

```
P0 / P1 告警触发
      │
      ▼
1. 从 main 最新 tag 拉 hotfix/v1.0.1-{desc}
      │
      ▼
2. 修复 + 单元测试 + 核心链路回归（< 2h）
      │
      ▼
3. 提交 PR → main（🔴 紧急通道：2 人 approve，可跳过非核心 CI）
      │
      ▼
4. 合并 → 打 tag v1.0.1 → 立即部署医院现场
      │
      ▼
5. 验证生产环境恢复（监控 30 min）
      │
      ▼
6. hotfix → develop 反向合并（**不可跳过**）
      │
      ▼
7. 24h 内补写 RCA（根因分析）→ 归档到 docs/rcas/
```

#### 15.7.3 RCA 模板

```markdown
# RCA: v1.0.1 — 结算防重锁失效导致重复结算

**时间**: 2026-07-15 14:32
**级别**: P0
**发现人**: 人民医院检验科 张老师
**处理人**: 张三

## 现象
申领单 REQ-20260715-042 被结算两次，产生两条 settlement_record。

## 根因
Redis 锁的 TTL 设置过短（5s），数据库唯一索引缺失 settlement_req_id 列。

## 修复
1. 延长锁 TTL 至 30s（hotfix/v1.0.1-settlement-duplicate）
2. settlement_record.settlement_req_id 加 UNIQUE KEY（Flyway V2026071501）

## 预防措施
- [ ] CI 增加并发结算集成测试用例
- [ ] Code Review 增加"防重设计"专项检查项

## 影响范围
仅 REQ-20260715-042 一笔，已冲销恢复。
```

---

### 15.8 分支保护规则

| 分支 | 直接 Push | Force Push | 删除分支 | 合并方式 | 最少 Approve | 要求 CI |
|------|:---:|:---:|:---:|------|:---:|:---:|
| `main` | ❌ | ❌ | ❌ | Merge Commit (保留历史) | **2 人** | ✅ 全量 |
| `develop` | ❌ | ❌ | ❌ | Squash Merge (保持线性) | 1 人 | ✅ 全量 |
| `release/*` | ❌ | ❌ | 发布后删除 | Merge Commit | **2 人** | ✅ 全量 |
| `hotfix/*` | ✅ | 允许（需审批） | 修复后删除 | Merge Commit | **2 人** | ✅ 核心链路 |
| `feature/*` | ✅ | 允许 | 合并后删除 | Squash Merge | 1 人 | ✅ 单元测试 |
| `bugfix/*` | ✅ | 允许 | 合并后删除 | Squash Merge | 1 人 | ✅ 单元测试 |
| `hospital/*` | ✅ | 禁止 | 长期保留 | Merge Commit | 1 人 | ✅ 按需 |

> **解释**：`develop` 用 Squash Merge 保持干净线性历史；`main` / `release` 用 Merge Commit 保留完整的合并记录，满足医疗审计追溯要求。

---

### 15.9 迭代节奏

| 周期 | 时长 | 内容 | 产出物 |
|------|:--:|------|--------|
| **Sprint** | 2 周 | 1-3 个 feature + bugfix | 可演示的增量 |
| **Release** | 4-6 周 | 2-3 个 sprint 合并为一个 release | 可交付的 tag |
| **功能冻结** | 发布前 3 天 | 仅修 release 分支缺陷 | 稳定的 release 分支 |
| **Hotfix** | 按需 | 发现即修，P0 30min / P1 2h | PATCH 版本 tag |
| **大版本** | 6-12 月 | MAJOR 版本升级（一期→二期） | 新基线 tag |
| **回顾会议** | 每 release 后 | 回顾 RCA、优化流程 | 流程改进项 |

---

### 15.10 医院定制版管理

医院现场可能存在差异化需求（HIS/LIS 接口字段不同、本地报表格式等），通过 `hospital/*` 分支管理：

```
main: v1.1.1 (基线)
│
├── hospital/rjyy-v1.1.1    ← 人民医院（HIS 接口多了 patient_region 字段）
├── hospital/zyyy-v1.1.1    ← 中医院（报表格式调整）
│
└── 通用修复 → cherry-pick → 定制分支同步
```

**规则**：
- 医院定制分支 **只能改集成层代码**（`ivd-integration` 模块），不改核心业务逻辑
- 定制内容在文件顶部标注 `// HOSPITAL: <医院名> — <变更说明>`
- 基线 bugfix 通过 `cherry-pick` 同步到各定制分支
- 定制分支原则上**不合并回 main**，除非该定制具有通用性

---

### 15.11 多版本并存策略

医院现场不强制升级，旧版本需维持可维护状态：

```
v1.0.0 (3 家医院)  →  仅接受 P0 热修复
v1.1.0 (5 家医院)  →  接受 P0/P1 修复
v1.2.0 (最新)      →  完整支持
```

| 版本生命周期 | 时长 | 支持范围 |
|------|:--:|------|
| 活跃期 | 0-3 月 | 全部支持 |
| 维护期 | 3-6 月 | P0/P1 修复 |
| 归档期 | 6+ 月 | 仅 P0（建议升级） |

---

### 15.12 CI 检查项

每次 Push / PR 自动触发：

| 检查项 | feature | develop | release | main |
|------|:---:|:---:|:---:|:---:|
| 编译 (Maven / Vite) | ✅ | ✅ | ✅ | ✅ |
| 单元测试 (JUnit + Vitest) | ✅ | ✅ | ✅ | ✅ |
| 代码风格 (Checkstyle / ESLint) | ✅ | ✅ | ✅ | ✅ |
| Mapper DAO 测试 (@MybatisPlusTest) | ✅ | ✅ | ✅ | ✅ |
| 核心链路集成测试 | ❌ | ✅ | ✅ | ✅ |
| 安全扫描 (依赖漏洞) | ❌ | ❌ | ✅ | ✅ |
| 文件行数检查 (>500 行警告) | ✅ | ✅ | ✅ | ✅ |
| Flyway 脚本重复检测 | ✅ | ✅ | ✅ | ✅ |

---

## 16. Code Review 清单

### 16.1 后端 Review 必检项

- [ ] Controller 是否只做了参数校验和调用 Service（无业务逻辑）
- [ ] Service 事务边界是否正确（`@Transactional` 覆盖所有 DB 写操作）
- [ ] 状态转移是否调用了 `validateTransition`
- [ ] 是否在同一个事务内更新状态 + 写不可变日志
- [ ] 日期时间字段是否使用了 `DATETIME(3)`（毫秒精度）
- [ ] 金额字段是否使用了 `BigDecimal`
- [ ] 枚举字段是否在 DB 中设为 `NOT NULL`
- [ ] Flyway 脚本是否按规范命名，新旧脚本不冲突
- [ ] 是否有 SQL 使用 MySQL 私有扩展函数（如 `GROUP_CONCAT`）
- [ ] Redis Key 是否符合命名规范，是否有 TTL
- [ ] 是否有患者明文信息出现在日志/返回值中
- [ ] 核心业务节点是否有 INFO 日志
- [ ] 新增/修改 API 是否有 MockMvc 测试
- [ ] 核心链路是否有集成测试

### 16.2 前端 Review 必检项

- [ ] 组件命名是否符合 PascalCase
- [ ] API 调用是否封装在 `@/api/` 目录
- [ ] 表格是否分页（默认 10 条）
- [ ] 金额是否格式化（千分位 + ¥ 符号）
- [ ] 审批状态是否使用颜色圆点（含脉动动画）
- [ ] 表单必填项是否有红色 `*` 标识
- [ ] 操作成功/失败是否有 Toast 提示
- [ ] 高危操作是否有二次确认弹窗

### 16.3 数据库 Review 必检项

- [ ] 引擎是否 InnoDB
- [ ] 字符集是否 utf8mb4
- [ ] 时间字段是否 DATETIME(3)
- [ ] 金额字段是否 DECIMAL(12,2)
- [ ] 外键列是否有索引
- [ ] 不可变日志表是否 APPEND-ONLY（无 UPDATE/DELETE 权限）
- [ ] 预留二期表是否标注 `TODO: Phase 2`

---

## 17. 代码注释与文件组织规范

> 注释是代码的"为什么"，而非"是什么"。代码本身应自解释，注释负责补充意图、约束和业务上下文。

---

### 17.1 总则

| 原则 | 说明 |
|------|------|
| **注释即债务** | 代码变更时必须同步更新注释，过时注释比无注释更危险 |
| **说"为什么"** | 注释解释业务意图和设计决策，不重复代码逻辑 |
| **风格统一** | 同一语言内注释风格必须一致，禁止混用多种注释风格 |
| **禁止注释掉的代码** | 废弃代码直接删除，Git 历史可追溯，不得以注释形式保留 |

---

### 17.2 Java (后端) 注释规范

#### 17.2.1 类级别 JavaDoc（必须）

所有 `public` 类必须有 JavaDoc，包含功能描述、业务模块归属、作者：

```java
/**
 * 试剂目录管理服务
 *
 * 负责试剂/校准品/质控品/耗材的增删改查、五码映射绑定、
 * 以及目录变更审批流程的编排。
 *
 * @author 张三
 * @since 2026-07-01
 */
@Service
public class ReagentCatalogServiceImpl implements ReagentCatalogService {
```

#### 17.2.2 方法级 JavaDoc

`public` 方法必须写 JavaDoc，说明业务语义、参数含义、返回值及异常：

```java
/**
 * 按条件分页查询试剂目录列表
 *
 * @param query 支持 hisCode / genericName / materialType / approvalStatus 组合筛选
 * @return 分页结果，含五码映射摘要
 * @throws IllegalArgumentException 当 query.page < 1 时抛出
 */
@Override
public PageVO<ReagentCatalogVO> pageList(ReagentCatalogQuery query) {
```

**豁免项**：Controller 的 CRUD 方法、getter/setter、已用 `@ApiOperation` (Swagger) 标注的方法可省略 JavaDoc。

#### 17.2.3 字段注释

Entity/DTO/VO 中的字段应使用行尾注释说明业务含义：

```java
private String hisCode;                        // 院内编码（格式: IVD-{组别缩写}-{序号}）
private MaterialTypeEnum materialType;         // 物料类型（枚举 → DB ENUM）
private BigDecimal pricePerUnit;               // 单价（元），含税
private ApprovalStatusEnum approvalStatus;     // 审批状态
```

#### 17.2.4 步骤注释

复杂业务方法内使用 `// 1.` `// 2.` 编号标注关键步骤，提高可读性：

```java
public SettlementResult settle(String reqNo) {
    // 1. 防重校验
    // 2. 加载申领明细
    // 3. 逐项结算（含 FEFO 扣减）
    // 4. 生成结算记录（settlement_record）
    // 5. 发布结算完成事件
}
```

**规则**：
- 步骤注释放在对应代码块上方，独占一行
- 编号连续，不超过 10 步；超过 10 步应拆分为私有方法
- 步骤注释后面必须跟对应代码，禁止空步骤

#### 17.2.5 特殊标记

| 标记 | 用途 | 示例 |
|------|------|------|
| `// FIXME:` | 已知缺陷，需后续修复 | `// FIXME: 并发场景下存在竞态条件，需加分布式锁` |
| `// TODO:` | 规划内待完成项 | `// TODO: Phase 2 对接阳光采购平台` |
| `// XXX:` | 临时方案/技术债，需重构 | `// XXX: 当前全表扫描，二期加索引后改为范围查询` |
| `// NOTE:` | 重要说明，防止误改 | `// NOTE: 此排序规则与 FEFO 计算强耦合，勿改` |

---

### 17.3 Vue / JavaScript (前端) 注释规范

#### 17.3.1 组件顶部注释

每个 `.vue` 文件顶部使用 JSDoc 注释说明组件用途：

```vue
<!--
  CatalogList.vue — 试剂目录列表页

  功能：分页展示 + 多条件筛选 + 批量操作入口
  路由：/catalog
  依赖组件：CatalogFilter, UdiFillModal, ApprovalBadge
-->
<script setup>
```

#### 17.3.2 函数注释

`api/` 目录下的接口函数必须注释其业务用途：

```javascript
/**
 * 分页查询试剂目录
 * @param {Object} params - 查询参数（hisCode, genericName, materialType, page, pageSize）
 * @returns {Promise<PageResult>} 分页结果，records 含五码映射摘要
 */
export function getCatalogList(params) {
    return request({ url: '/api/v1/catalog', method: 'get', params })
}
```

#### 17.3.3 复杂逻辑注释

Pinia Store 中的 `action` 以及 `utils/` 中的工具函数，对非显而易见的逻辑必须加注释：

```javascript
// 计算移动加权平均成本：新成本 = (原库存×原均价 + 本次数量×本次单价) / (原库存 + 本次数量)
const newAvgCost = (prevStock * prevAvgCost + qty * unitPrice) / (prevStock + qty)
```

#### 17.3.4 模板注释

Vue `<template>` 中，对条件渲染影响业务结果的分支应加注释：

```html
<!-- 当库存 < 最低库存的 50% 时显示紧急采购入口 -->
<el-button v-if="stock < minStock * 0.5" type="danger" @click="urgentProcurement">
  紧急采购
</el-button>
```

---

### 17.4 SQL 注释规范

#### 17.4.1 Flyway 迁移脚本

每个迁移脚本文件顶部必须有注释块：

```sql
-- ============================================
-- 脚本: V20260701__init_reagent_catalog.sql
-- 说明: 初始化试剂目录表 + 预置五码映射枚举
-- 模块: M01 试剂目录
-- 依赖: 无（一期建表基线）
-- ============================================
```

#### 17.4.2 表与字段注释

```sql
CREATE TABLE reagent_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    his_code VARCHAR(50) NOT NULL COMMENT '院内编码（IVD-{组别}-{序号}）',
    generic_name VARCHAR(200) NOT NULL COMMENT '物料名称',
    material_type ENUM('REAGENT','CALIBRATOR','QC_MATERIAL','CONSUMABLE','OTHER')
        NOT NULL COMMENT '物料类型',
    ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试剂目录主表';
```

**规则**：
- 所有表和字段必须有 `COMMENT`
- `COMMENT` 写中文业务含义，不写技术描述
- 枚举字段的 `COMMENT` 需列出可选值范围

#### 17.4.3 复杂查询注释

存储过程或 30 行以上的查询语句必须用 `--` 标注每段的业务意图：

```sql
-- 1. 查询有效期内的活跃批次
SELECT ...
-- 2. 按 FEFO 排序取最早过期批次
ORDER BY expiry_date ASC
-- 3. 仅返回库存 > 0 的记录
WHERE stock > 0
```

---

### 17.5 注释禁止项

| ❌ 禁止 | ✅ 正确做法 |
|--------|------------|
| 注释掉的代码块 | 直接删除，需要时从 Git 找回 |
| `// i++` 之类的废话注释 | 不写，代码自解释 |
| 注释与代码不一致 | 改代码时必须同步改注释 |
| 整段日志/敏感信息注释在代码中 | 敏感信息走审计日志，不写入源码 |
| 注释中使用非 ASCII 的特殊字符 | 统一使用 UTF-8 中文 |
| 超过 3 个月未处理的 `TODO` | 要么排期实现，要么删除 |
| 空注释（`/** */` 内无内容） | 补充说明或删除 |

---

### 17.6 文件组织规范：按功能节点拆分

> **所有前后端代码必须按功能节点拆分到独立文件，禁止将多个不相关的类/组件/函数堆砌在单一文件中。**

#### 17.6.1 后端文件拆分（Java）

##### 强制规则

| 文件类型 | 每个文件放几个 | 说明 |
|---------|-------------|------|
| `*Controller.java` | **1 个 Controller** | 每个 REST 资源对应一个 Controller 文件 |
| `*Service.java` | **1 个接口** | 每个业务聚合对应一个 Service 接口 |
| `*ServiceImpl.java` | **1 个实现** | 与接口一一对应 |
| `*Mapper.java` | **1 个 Mapper** | 每个实体对应一个 Mapper 接口 |
| `*Mapper.xml` | **1 个 XML** | 与 Mapper 一一对应 |
| `*Request.java` | **1 个 DTO** | 每个接口的请求体独立定义 |
| `*VO.java` | **1 个 VO** | 每个接口的响应体独立定义 |
| `*Enum.java` | **1 个枚举** | 每个枚举类型一个文件 |
| `*Config.java` | **1 个配置类** | 每个配置关注点一个文件 |
| `*Event.java` | **1 个事件类** | 每个 Spring Event 类型一个文件 |
| `*Exception.java` | **1 个异常类** | 每个业务异常一个文件 |

##### 反面示例（禁止）

```java
// ❌ 禁止：多个 Controller 混在一个文件
// file: AllCatalogControllers.java
@RestController
public class ReagentController { ... }       // 试剂目录

@RestController
class CalibratorController { ... }            // 校准品目录（第二个 class）

@RestController
class QcMaterialController { ... }            // 质控品目录（第三个 class）
```

```java
// ❌ 禁止：多个 DTO 混在一个文件
// file: CatalogDtos.java
public class CreateReagentRequest { ... }
public class UpdateReagentRequest { ... }
public class ReagentQuery { ... }
```

##### 正面示例（必须）

```
ivd-catalog/src/main/java/com/zlth/ivd/catalog/
├── controller/
│   ├── ReagentCatalogController.java        ← 只含试剂目录 CRUD
│   └── FiveCodeMappingController.java       ← 只含五码映射操作
├── service/
│   ├── ReagentCatalogService.java           ← 接口
│   ├── FiveCodeMappingService.java          ← 接口
│   └── impl/
│       ├── ReagentCatalogServiceImpl.java   ← 实现
│       └── FiveCodeMappingServiceImpl.java  ← 实现
├── mapper/
│   ├── ReagentCatalogMapper.java
│   └── FiveCodeMappingMapper.java
├── entity/
│   ├── ReagentCatalog.java
│   └── FiveCodeMapping.java
├── dto/
│   ├── CreateReagentRequest.java
│   ├── UpdateReagentRequest.java
│   ├── ReagentCatalogQuery.java
│   ├── ReagentCatalogVO.java
│   └── FiveCodeMappingVO.java
├── enums/
│   ├── MaterialTypeEnum.java
│   └── ApprovalStatusEnum.java
├── config/
│   └── CatalogAutoConfig.java
├── event/
│   ├── CatalogCreatedEvent.java
│   └── CatalogApprovedEvent.java
└── exception/
    └── DuplicateHisCodeException.java
```

#### 17.6.2 前端文件拆分（Vue 3）

##### 强制规则

| 文件类型 | 每个文件放几个 | 说明 |
|---------|-------------|------|
| `*.vue` (页面) | **1 个页面组件** | 每个路由对应一个 view 文件 |
| `*.vue` (弹窗) | **1 个弹窗** | 每个 Modal/Drawer 独立文件 |
| `*.vue` (通用组件) | **1 个组件** | 每个可复用 UI 组件一个文件 |
| `api/*.js` | **1 个模块** | 每个业务模块的 API 封装一个文件 |
| `stores/*.js` | **1 个 Store** | 每个业务模块的 Pinia Store 一个文件 |
| `utils/*.js` | **按功能域** | 每个工具域（日期/格式化/校验）一个文件 |
| `router/*.js` | **1 个路由配置** | 整个模块的路由定义一个文件 |
| `*.css` / `*.scss` | **按组件** | 页面级样式写在该 `.vue` 的 `<style scoped>` 中 |

##### 反面示例（禁止）

```vue
<!-- ❌ 禁止：一个 .vue 文件包含多个不相关的页面 -->
<template>
  <!-- 试剂目录列表 -->
  <div v-if="page === 'catalog'">...</div>
  <!-- 证照管理列表 -->
  <div v-else-if="page === 'license'">...</div>
  <!-- 采购订单列表 -->
  <div v-else-if="page === 'procurement'">...</div>
</template>
```

```javascript
// ❌ 禁止：一个 api 文件混合多个不相关模块
// file: api/all-in-one.js
export function getCatalogList(params) { ... }      // M01
export function getLicenseList(params) { ... }      // M03
export function getProcurementList(params) { ... }  // M04
export function getInventoryList(params) { ... }    // M06
```

##### 正面示例（必须）

```
src/
├── views/
│   ├── catalog/
│   │   ├── CatalogList.vue              ← 试剂目录列表页
│   │   ├── CatalogEdit.vue              ← 试剂编辑页
│   │   └── UdiBacklogPanel.vue          ← UDI 待补全面板
│   ├── license/
│   │   ├── LicenseList.vue              ← 证照列表页
│   │   └── LicenseEdit.vue              ← 证照编辑页
│   ├── procurement/
│   │   ├── ProcurementList.vue          ← 采购订单列表
│   │   ├── PurchaseOrderEdit.vue        ← PO 编辑页
│   │   └── AcceptanceModal.vue          ← 扫码验收弹窗
│   └── ...
├── components/
│   ├── common/
│   │   ├── StatusBadge.vue              ← 通用状态徽章
│   │   └── ConfirmDialog.vue            ← 通用确认弹窗
│   └── business/
│       ├── MaterialSelector.vue         ← 物料选择器
│       └── BatchSelector.vue            ← 批次选择器
├── api/
│   ├── catalog.js                       ← M01 试剂目录 API
│   ├── license.js                       ← M03 证照管理 API
│   ├── procurement.js                   ← M04 采购管理 API
│   └── ...
├── stores/
│   ├── catalog.js                       ← 试剂目录 Store
│   ├── license.js                       ← 证照管理 Store
│   └── ...
├── router/
│   └── index.js                         ← 路由配置
└── utils/
    ├── format.js                        ← 金额/日期/状态格式化
    ├── validate.js                      ← 表单校验规则
    └── request.js                       ← Axios 封装
```

#### 17.6.3 拆分判断标准

| 何时拆分 | 何时不拆 |
|---------|---------|
| ✅ 不同业务模块（如 M01 vs M03） | ❌ 紧密耦合的内部辅助类（如 Builder） |
| ✅ 不同技术层级（Controller vs Service） | ❌ 仅 2-3 行的简单枚举（可放在父类同文件中，但必须在文件顶部注释说明） |
| ✅ 不同 API 资源（`/catalog` vs `/license`） | ❌ 功能完全相同的衍生组件（用 props 控制变体） |
| ✅ 单个文件超过 **500 行** | |
| ✅ 单个文件包含 **2 个及以上 public class** | |
| ✅ 单文件包含 **不相关的函数/逻辑** | |

---

### 17.7 文件组织禁止项

| ❌ 禁止 | ✅ 正确做法 |
|--------|------------|
| 一个文件包含多个 `@RestController` 类 | 每个 Controller 独立文件 |
| 一个文件包含多个 `public class`（内部类除外） | 每个公共类独立文件 |
| 一个 `.vue` 文件通过 `v-if` 切换多个不相关页面 | 每个页面独立 `.vue` 文件 |
| 一个 `api.js` 混合所有模块的接口 | 按模块拆分 `api/` 文件 |
| 一个 Store 管理全局所有状态 | 按模块拆分 Store |
| 一个 `utils.js` 堆砌所有工具函数 | 按功能域拆分（format / validate / date） |
| 单文件超过 500 行且包含多个独立逻辑块 | 按职责拆分 |

---

> **检查机制**：CI 中引入文件行数检查（单文件 > 500 行触发 warning），Code Review 中对照 17.6.3 拆分判断标准逐项确认。

---

## 附录 A：PRD 中定义的枚举值全集

### A.1 material_type（物料类型）

| 值 | 含义 | UDI | 注册证 | 标称测试数 | 验收方式 | 进入状态机 |
|----|------|-----|--------|-----------|---------|-----------|
| `REAGENT` | 试剂 | 必填 | 必填 | 必填 | 扫码验收 | ✅ |
| `CALIBRATOR` | 校准品 | 必填 | 必填 | 不必填 | 扫码验收 | ✅ |
| `QC_MATERIAL` | 质控品 | 必填 | 必填 | 不必填 | 扫码验收 | ✅ |
| `CONSUMABLE` | 耗材 | 不需要 | 不需要 | 不需要 | 数量验收 | ❌ |
| `OTHER` | 其他 | 不需要 | 不需要 | 不需要 | 数量验收 | ❌ |

### A.2 approval_status（目录审批状态）

| 值 | 含义 |
|----|------|
| `DRAFT` | 草稿 |
| `PENDING` | 待审批 |
| `APPROVED` | 已批准 |
| `REJECTED` | 已驳回 |

### A.3 settlement_status（X2 结算状态）

| 值 | 含义 |
|----|------|
| `PENDING` | 待结算 |
| `SETTLED` | 已结算 |
| `REVERSED` | 已冲销 |

### A.4 settlement_type（结算类型）

| 值 | 含义 |
|----|------|
| `REQUISITION` | 申领结算 |
| `CONSUMPTION` | 领用结算 |

### A.5 settlement_record.status（结算记录状态）

| 值 | 含义 |
|----|------|
| `ACTIVE` | 有效结算 |
| `REVERSED` | 已冲销 |

### A.6 UDI 状态（11 种）

`RECEIVED` | `IN_STOCK` | `PENDING_UDI` | `TRANSFERRED` | `IN_USE` | `CONSUMED` | `RETURNED` | `EXPIRED` | `RECALLED` | `BACK_SUPPLIER` | `SCRAPPED` | `DESTROYED`

### A.7 审核决策动作（4 种）

| 值 | 含义 | 优先级 |
|----|------|--------|
| `AUTO_RELEASE` | 自动放行 | 1 |
| `WARN_RELEASE` | 放行带警告 | 2 |
| `HOLD_REVIEW` | 转人工审核 | 3 |
| `REJECT_RETEST` | 拒绝需复测 | 4 |

### A.8 证照状态

| 值 | 含义 |
|----|------|
| `ACTIVE` | 有效 |
| `EXPIRING` | 即将到期 |
| `EXPIRED` | 已过期 |
| `FROZEN` | 已冻结 |

### A.9 证照类型

`PRODUCT_REG` | `PRODUCTION` | `OPERATION` | `GMP` | `GSP` | `OTHER`

### A.10 采购订单状态

`DRAFT` | `SENT` | `PARTIAL_RECEIVED` | `COMPLETED` | `CANCELLED`

### A.11 申领单状态

`DRAFT` | `PENDING_REVIEW` | `REVIEWED` | `PICKING` | `PARTIAL_FULFILLED` | `FULFILLED` | `SHORTAGE_PENDING` | `CANCELLED`

### A.12 缺货单状态

`PENDING_PROCUREMENT` | `PURCHASING` | `REPLENISHED` | `COMPLETED` | `CANCELLED`

### A.13 五码映射状态

`MAPPED` | `PENDING_MAPPING` | `INSURANCE_PENDING`

### A.14 设备关联模式

`FORCE` | `FLEXIBLE`

### A.15 库存紧急程度

`NORMAL` | `URGENT` | `CRITICAL`

### A.16 消耗用途

`ROUTINE` | `BATCH_TEST` | `EQUIP_MAINT` | `DEPT_USE` | `OTHER`

### A.17 验收结果

`QUALIFIED` | `PARTIAL` | `UNQUALIFIED`

---

## 附录 B：PRD 中定义的单号格式全集

| 单号类型 | 格式 | 示例 | 生成模块 |
|---------|------|------|---------|
| 申领单号 | `REQ-YYYYMMDD-NNN` | `REQ-20260630-001` | M04 |
| 缺货单号 | `SH-YYYYMMDD-NNN` | `SH-20260630-001` | M04 |
| 采购订单号 | `PO-YYYYMMDD-NNNN` | `PO-20260630-0001` | M04 |
| 结算单号 | `STL-YYYYMMDD-NNNN` | `STL-20260630-0001` | X2 |
| 虚拟批号（耗材） | `BATCH-CONS-YYYYMMDD-NNN` | `BATCH-CONS-20260630-001` | M04 |
| 院内编码 | `IVD-{组别缩写}-{序号}` | `IVD-BIO-001` | M01 |

---

> **规范效力**：本手册中的"必须"条款为硬性约束（code review 不过则驳回），"禁止"条款为红线（CI 加 Lint 规则），"建议"条款为软性约束（review 中讨论）。
>
> **更新机制**：PRD 版本更新时，本手册同步更新。任何开发者发现规范与 PRD 冲突，以 PRD 为准并提请更新本手册。
>
> **文档版本记录**
> V1.0-DEV — 2026-06-30 — 初稿，基于 IVD 一期 MVP PRD V1.3 + 二期规划 PRD V1.0-Phase2
