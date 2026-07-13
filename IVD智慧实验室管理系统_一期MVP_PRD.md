# IVD 智慧实验室管理系统 — 一期 MVP 产品需求规格说明书（PRD）

> **版本**: V1.2-MVP
> **日期**: 2026-06-29
> **文档状态**: 定稿（一期范围，V1.2 文档精简 + 二期分离）
> **密级**: 内部
> **定位**: 创业首版可交付产品。目标——3 个月交付、能卖钱、有差异化。
>
> **V1.0 → V1.1 → V1.2 变更摘要**：
> - V1.1：引入 `material_type` 物料类型枚举、五码映射按物料类型区分、UDI 待补全清单、审批影响分析、自动拆单、数量验收、耗材消耗、PENDING_UDI 状态
> - V1.2：剔除 UX 导航规范（移入效果图 HTML 说明）、精简 UI 组件树为设计要点、移除部署验证/加固章节（详见 `deploy/README.md`）、分离二期功能至独立文档《IVD智慧实验室管理系统_二期规划_PRD》

---

## 0. 阅读指引

本文档是《IVD 智慧实验室全流程管理系统 PRD V1.5》（以下简称"全本 PRD"）的一期范围收口版。**全本 PRD 描述终局蓝图，本文档描述首版交付。** 任何冲突，以本文档为准。

---

## 目录

- [1. 项目定位与产品策略](#1-项目定位与产品策略)
- [2. 一期范围界定](#2-一期范围界定)
- [3. 用户角色与核心场景](#3-用户角色与核心场景)
- [4. 模块详述](#4-模块详述)
- [5. ★ 特色一：效益分析模块](#5--特色一效益分析模块)
- [6. ★ 特色二：批次感知自动审核模块](#6--特色二批次感知自动审核模块)
- [7. 核心数据模型](#7-核心数据模型)
- [8. 系统集成接口契约](#8-系统集成接口契约)
- [9. 非功能需求](#9-非功能需求)
- [10. 实施路线（13 周）](#10-实施路线13-周)
- [11. 验收标准](#11-验收标准)
- [12. 风险识别与应对](#12-风险识别与应对)
- [附录 A：与全本 PRD 的范围差异对照](#附录-a与全本-prd-的范围差异对照)
- [附录 B：ISO 15189 一期合规矩阵](#附录-biso-15189-一期合规矩阵)
- [附录 C：销售用 ROI 测算逻辑](#附录-c销售用-roi-测算逻辑)

---

## 1. 项目定位与产品策略

### 1.1 一句话定位

> **以 UDI 批次追溯为数据地基的进销存系统，叠加"批次感知"三大特色：效益分析（成交器）+ 统一规则引擎（大脑）+ 自动审核（钩子）。**

### 1.2 产品逻辑链（一切设计的出发点）

```
              【进销存 = 数据地基】
        UDI 批次级追溯 + 真实消耗记录
                    │
        ┌───────────┼───────────────┐
        ▼           ▼               ▼
   【效益分析】   【规则设置】     【自动审核】
   = 成交器       = 大脑           = 钩子
   证明值得买    让系统变聪明     让人离不开
        │           │               │
        └───────────┴───────────────┘
                    ▼
              【AI 智能化】= 终局天花板
          （政策 + 数据飞轮，二三期演进）
```

### 1.3 差异化护城河

**"批次感知"——纯 LIS、纯进销存软件都做不到。**

| 特色 | 普通软件能做到 | 本系统因有批次数据能做到（差异化） |
|---|---|---|
| 效益分析 | 标称成本（采购价÷标称测试数） | **实际成本**（含过期/开瓶/复测/质控损耗），告诉主任"A 牌比 B 牌贵 15%，因为开瓶浪费多" |
| 自动审核 | 按参考范围/delta check 放行 | **批次感知拦截**：质控失控批次、近效期、开瓶超时的结果自动转人工 |
| 规则设置 | 各管各的规则 | **跨域联动**：质控失控→自动触发结果拦截；证照异常→冻结采购 |

**销售话术**：
> "我们的自动审核知道你今天用的是哪一批试剂、它质控过没过——你的 LIS 做不到，因为它不知道上机的是哪个批次。"

### 1.4 政策抓手

| 政策 | 一期如何响应 |
|---|---|
| UDI 全面推行（2025 三类→2027 全覆盖） | 一期核心即 UDI 批次追溯 |
| DRG/DIP 支付改革 | 效益分析模块直接对标"成本中心"管理诉求 |
| ISO 15189 质量认可 | 自动审核规则库对标质控要求（完整质控闭环放二期） |


---

## 2. 一期范围界定

### 2.1 在范围内（10 个模块）

**地基模块（6 个，进销存闭环）**

- ✅ M01 试剂目录管理 + 五码映射
- ✅ M03 证照管理（电子化 + 效期预警）
- ✅ M04 采购管理（申领→审核→订单→扫码验收）
- ✅ M06 三级库管理（中心库/科室库/组库 + FEFO）
- ✅ M08 上机与消耗管理（**手动扫码上机 + 手动/批量核销**，不依赖设备上报）
- ✅ M09 进销存日清月结（**指标放宽**，见 4.6）

**特色模块（3 个，护城河）**

- ✅ S1 效益分析（成本模型 + 三张核心报表）
- ✅ S2 统一规则引擎（库存/质控/兼容/审核规则统一配置 + 联动）
- ✅ S3 批次感知自动审核（L1 结果规则 + L2 批次规则 + LIS 接口）

**贯穿模块（1 个）**

- ✅ X1 UDI 追溯主线（M04→M05→M06→M08 全流程状态机）

### 2.2 不在范围内（明确砍掉 / 推迟）

| 砍掉项                       | 原因                | 替代方案                      |
| ------------------------- | ----------------- | ------------------------- |
| ❌ 自动核销（依赖设备上报吸样量）         | 绝大多数设备不上报吸样量，落地不了 | 手动核销 + 批量核销               |
| ❌ 仪器直连接入（200+ 设备接口）       | 最大交付黑洞，拖垮项目       | **走 LIS 旁路取结果数据**         |
| ❌ 室内质控执行（IQC Westgard 引擎） | 二期专题              | 一期仅做**规则配置**，执行放二期        |
| ❌ 室间质评（EQA）               | 二期                | —                         |
| ❌ POCT 综合管理               | 二期                | —                         |
| ❌ DeepSeek AI             | 三期（数据飞轮成熟后）       | 一期效益分析的"规则版建议"即 AI 雏形     |
| ❌ FHIR 全集成 / Camunda 工作流  | 三期                | 一期用轻量审批流 + 自定义 LIS/HIS 接口 |
| ❌ 主任驾驶舱完整版                | 三期                | 一期做效益分析 + 审核统计两张核心看板      |

### 2.3 一期不做但要"预留"

为了二期平滑演进，一期数据模型/API **预留扩展位**（不实现功能，但留接口）：
- 质控结果表 `qc_result`（空表，二期填充）
- POCT 设备表 `poct_device`（空表）
- AI 调用日志表 `ai_consultation_log`（空表）
- 仪器接口网关占位（接口定义但不接入真实设备）

---

## 3. 用户角色与核心场景

### 3.1 一期角色（精简至 8 个）

| 角色 | 职责 | 使用模块 |
|---|---|---|
| 系统管理员 | 配置、用户、规则维护 | 全部 |
| 采购员 | 采购订单、供应商沟通 | M01/M03/M04 |
| 库管员 | 验收、库存、调拨、盘点 | M04/M06/M09 |
| 检验技师 | 扫码上机、消耗核销 | M08 |
| 检验医师 | 人工审核（审核队列消费方） | S3 |
| 检验科主任 | 看板、审批、效益分析 | S1/M09 |
| 财务人员 | 进销存对账 | M09 |
| 信息科 | LIS/HIS 接口联调 | S3/接口 |

> 角色权限矩阵沿用全本 PRD 3.2 节，一期裁剪掉 POCT 管理员/操作员/护士/设备管理员等角色。

### 3.2 三个核心业务场景（一期必须跑通）

#### 场景一：试剂从买到用的全追溯（地基场景）

```
主任发现某项目成本高
  → 查效益分析报表，定位到"A 牌 CK-MB 单测试成本偏离 35%"
  → 下钻批次，发现 BATCH-2025A 开瓶损耗占 20%
  → 调整采购规格为小包装
  → 下月浪费率下降 → 报表验证改进效果
```

#### 场景二：批次感知自动审核（护城河场景）

```
技师在 AU5800 上机 BATCH-2025A（扫码，系统记录设备×批次×时间区间）
  → 09:15 该设备产出肌酐结果，LIS 推送到本系统审核
  → 系统查上机记录：该结果由 BATCH-2025A 产生
  → 查批次状态：BATCH-2025A 当前质控失控（人工标记或规则触发）
  → 自动决策 HOLD_REVIEW，回写 LIS
  → 检验医师审核队列出现该结果，标注"批次质控失控"
  → 技师复测，结果正常，人工放行
```

#### 场景三：合规追溯（政策场景）

```
药监局飞检：要求出示某试剂批次全流向
  → 扫 UDI 码或输入批号
  → 系统秒级输出：采购订单→验收→入库→调拨→上机设备→患者关联
  → 导出 PDF 追溯报告，合规通过
```

---

---

## 4. 模块详述

### 4.1 M01 试剂目录管理 + 五码映射

#### 4.1.1 核心字段（在原 PRD 基础上**补充效益分析必需字段**）

| 字段 | 必填 | 说明 | 一期重要性 |
|---|---|---|---|
| **material_type** | ✅ | **物料类型（★ 新增字段，决定全流程行为）** — 见下表 | **★ 地基字段** |
| his_code | ✅ | 院内编码 | 基础 |
| udi_di | ⚠️ | UDI-DI — **按 material_type 决定必填性** | 基础 |
| generic_name / manufacturer / registration_cert_no | ⚠️ | 基本信息 — **registration_cert_no 仅 REAGENT/CALIBRATOR/QC_MATERIAL 必填** | 基础 |
| **tests_per_kit** | ⚠️ | **标称测试数（用于成本计算）** — **仅 REAGENT 必填** | **★ 效益分析必需** |
| unit / storage_condition | ✅ | 单位/储存 | 基础 |
| min_stock_level / max_stock_level | ✅ | 库存警戒 | 基础 |
| **price_per_unit** | ✅ | **最新采购单价（用于成本计算）** | **★ 效益分析必需** |
| stability_days_after_open | ⚠️ | 开瓶稳定天数 — **仅 REAGENT 填写** | 批次规则必需 |
| **test_groups** | ✅ | **适用组别（多选）** | **★ 必需，至少选一个** |

**★ material_type 枚举及字段必填性矩阵：**

| material_type | 含义 | udi_di | registration_cert_no | tests_per_kit | stability_days | 验收方式 | UDI状态机 | 效益分析 |
|---|---|---|---|---|---|---|---|---|
| `REAGENT` | 试剂 | **必填** | 必填 | 必填 | 必填 | 扫码验收 | ✅ 进入 | ✅ 按项目分摊 |
| `CALIBRATOR` | 校准品 | **必填** | 必填 | 不必填 | 不必填 | 扫码验收 | ✅ 进入 | ❌ |
| `QC_MATERIAL` | 质控品 | **必填** | 必填 | 不必填 | 不必填 | 扫码验收 | ✅ 进入 | ❌ |
| `CONSUMABLE` | 耗材（吸头/反应杯/手套/采样管…） | 不需要 | 不需要 | 不需要 | 不需要 | **数量验收** | ❌ 不入 | ✅ 按科室均摊 |
| `OTHER` | 其他（打印纸/标签纸/消毒液…） | 不需要 | 不需要 | 不需要 | 不需要 | **数量验收** | ❌ 不入 | ✅ 按科室均摊 |

> **⚠️ 关键变更**：`tests_per_kit` 和 `price_per_unit` 是原 PRD 没有强调、但效益分析必须的参数。一期数据初始化时必须采集。
>
> **⚠️ 关键变更（V1.1）**：引入 `material_type` 字段从根本上区分检验科各类物料。试剂/校准品/质控品走 UDI 全流程追溯，耗材/其他走简化流程。该字段在新增物料时首选填写，后续所有模块（申领、采购、验收、消耗、效益分析）均据此分流。

#### 4.1.2 五码映射

沿用全本 PRD M02，五码：UDI-DI / GTIN / 医保编码 / NMPA 注册证 / 院内编码。

**按物料类型区分：**

| material_type | 五码映射要求 | 映射状态 |
|---|---|---|
| REAGENT / CALIBRATOR / QC_MATERIAL | **五码必须完整**（MAP-01：UDI-DI 为主键）。GTIN 允许为空（MAP-02）。医保编码允许暂缺，但医保结算前必须补全（MAP-03） | 已映射 / 待映射 / 医保待补全 |
| CONSUMABLE / OTHER | **无需五码映射**，该卡片在详情页灰色不可点击 | 不可用（N/A） |

> **MAP-06（新增）**：material_type 为 REAGENT/CALIBRATOR/QC_MATERIAL 且 udi_di 为空时，五码映射状态自动置为"待映射"并进入 UDI 待补全清单。此时生成采购订单将触发阻断，直到 UDI-DI 补全。

#### 4.1.3 功能操作描述

试剂目录管理是一期进销存闭环的数据地基，所有后续模块（采购、库存、效益分析、自动审核）均依赖此模块的数据质量。以下为完整的用户操作流程描述。

##### 4.1.3.1 试剂列表浏览与筛选

**入口**：侧边栏 → 试剂目录

**页面加载行为**：
1. 系统从 `reagent_catalog` 表分页加载试剂列表，默认每页 10 条，按 `updated_at DESC` 排序
2. 页面顶部显示工具栏：搜索框 + 组别筛选下拉 + 审批状态筛选下拉 + 设备关联策略筛选下拉
3. Tab 栏提供三个视图切换：目录列表（默认激活）/ 变更审批 / 审批历史
4. 列表视图下，每条试剂行的"审批状态"列显示当前状态：草稿/待审批/已批准/已驳回
5. 若某条试剂存在"待审批"的变更记录，该行背景高亮（浅黄色），提示有未处理的变更

**筛选条件组合**：
- 组别下拉：全部 / 生化 / 免疫 / 临检 / 微生物 / 分子
- 状态下拉：全部 / 已批准 / 待审批 / 草稿 / 已驳回
- 设备关联下拉：全部 / 强制关联 / 柔性关联
- 搜索框：模糊匹配 院内编码 / 试剂名称 / UDI-DI / 注册证号 / 厂家

**分页**：后端分页，返回 `total` / `page` / `pageSize` / `records`。前端显示"共 N 条记录 · 每页 M 条 · 第 X/Y 页"。

##### 4.1.3.2 新增物料

**触发**：点击页头「+ 新增物料」按钮

**操作流程**：
1. 系统弹出编辑表单弹窗（Modal），标题显示"新增物料"
2. ★ **首选步骤**：`material_type` 下拉选择器置于表单最顶部。选择后表单字段**动态显隐**：
   - 选择 REAGENT：五码映射区展开、tests_per_kit 出现、stability_days 出现、registration_cert_no 出现
   - 选择 CALIBRATOR / QC_MATERIAL：五码映射区展开、tests_per_kit 隐藏、stability_days 隐藏
   - 选择 CONSUMABLE / OTHER：五码映射区整体隐藏、tests_per_kit 隐藏、stability_days 隐藏、registration_cert_no 隐藏
3. 表单分为三个区域（Fieldset），见下方 4.1.4 组件说明
4. 用户填写必填字段（标注红色 `*`）：
   - 所有类型通用必填：院内编码、物料名称、厂家、检验组别、单价、储存条件
   - REAGENT 额外必填：UDI-DI、注册证号、标称测试数
   - CALIBRATOR / QC_MATERIAL 额外必填：UDI-DI、注册证号
5. 选填字段（按 material_type 动态显示）：检测方法、单位、五码映射各字段（仅 REAGENT/CALIBRATOR/QC_MATERIAL 可见）、设备关联策略、开瓶稳定天数、库存警戒值、采购提前期
6. 用户可选择两种提交方式：
   - **保存草稿**（`btnSaveDraft`）：物料记录写入 `reagent_catalog` 表，`approval_status = DRAFT`。用户后续可继续编辑并提交审批
   - **提交审批**（`btnSubmitApproval`）：必填「变更原因」文本域，提交后 `approval_status = PENDING`，物料记录写入表，同时生成一条审批待办
7. 提交成功后关闭弹窗，Toast 提示操作结果，列表自动刷新

**校验规则（按 material_type 分级）**：

*通用校验（所有类型）：*
- `his_code`：必填，唯一性校验（全表查重），格式 `IVD-{组别缩写}-{序号}`
- `generic_name`：必填，最大 200 字符
- `price_per_unit`：必填，正数，精度 2 位小数
- `min_stock_level` < `max_stock_level`：数值大小校验

*REAGENT / CALIBRATOR / QC_MATERIAL 额外校验：*
- `udi_di`：**必填**，GS1/MA 标准格式校验。若为空，阻断提交审批（可保存草稿）
- `registration_cert_no`：**必填**，格式校验（国械注准/进 + 年份 + 8 位数字）
- `tests_per_kit`：**仅 REAGENT 必填**，正整数，≥ 1
- `stability_days_after_open`：**仅 REAGENT**，若设备关联策略为强制关联，建议填写（警告级提示）

*CONSUMABLE / OTHER 校验：*
- udi_di / registration_cert_no / tests_per_kit / stability_days：**不显示、不校验**

##### 4.1.3.3 编辑试剂

**触发**：点击列表行操作列的「✏️ 编辑」按钮，或点击「👁️ 查看」按钮

**操作流程**：
1. 系统弹出编辑表单弹窗，标题显示"编辑试剂 — {院内编码}"
2. 表单预填当前试剂的所有字段值
3. 用户修改需变更的字段
4. **关键约束**：若用户修改了 `tests_per_kit` 或 `price_per_unit`，弹窗 footer 提示文案变更为红色告警：「⚠️ 修改标称测试数或单价将影响效益分析的成本计算结果，请谨慎操作」
5. 提交方式同新增：
   - **保存草稿**：直接更新记录，`approval_status = DRAFT`
   - **提交审批**：必填变更原因 → 生成审批待办，`approval_status = PENDING`。系统自动记录变更前后的值（存入 `catalog_change_log` 表）
6. **若试剂当前状态为"待审批"**：弹窗提示"该试剂有一条待审批的变更，提交新变更将覆盖旧的待审批记录"，用户确认后覆盖
7. 提交成功后关闭弹窗，Toast 提示，列表刷新

##### 4.1.3.4 删除/停用试剂

**触发**：点击行操作列的「删除」图标

**约束**：
- 若该试剂存在关联的库存记录（`reagent_batch` 表有未消耗批次），禁止删除，提示"该试剂存在在库批次，无法删除。建议停用。"
- 若该试剂无关联库存，软删除：`is_deleted = true`，审批状态变更为 `DELETED_PENDING`，需主任审批后生效
- 若该试剂被采购订单引用（存在未完成的采购单），禁止删除

**审批逻辑**：删除操作同样走审批流（见 4.1.5），避免误删。

##### 4.1.3.5 五码映射编辑

**入口**：目录列表下方「五码映射」卡片中的「✏️ 编辑映射」按钮

**操作**：
1. 弹窗直接定位到五码映射区域
2. 五码映射字段：UDI-DI / GTIN / 医保编码(27位) / NMPA 注册证编号 / 院内 HIS 编码
3. 「映射状态」下拉：已映射 / 待映射 / 医保待补全
4. **重要提示**：若医保编码为空，弹窗底部显示「医保编码暂缺——医保结算前必须补全（MAP-03 规则）」
5. 提交后五码映射更新，不触发试剂字段的审批流（映射数据独立审批）

---

#### 4.1.4 UI 设计要点

**页面布局**：
- 顶部工具栏：搜索框 + 组别筛选 + 新增/批量导入/导出按钮
- Tab 切换栏：目录列表（默认）| 变更审批 | 审批历史 | UDI 待补全
- 目录列表：表格展示试剂行，列含院内编码、试剂名称、厂家、注册证号、适用组别、标称测试数、单价、设备关联策略、审批状态、操作按钮
- 审批状态以颜色圆点区分：🟢已批准 / 🟡待审批(脉动动画) / ⚪草稿 / 🔴已驳回
- 底部五码映射卡片：展示当前选中试剂的五码映射表格，含编辑映射入口

**编辑弹窗**（新增/编辑共用）：
- 4 个分区：基本信息 → 五码映射 → 高级设置 → 变更说明
- 按 material_type 动态显隐字段（见 §4.1.3.2）
- 底部提供「保存草稿」「提交审批」两种提交方式

**关键交互**：
- 新增/编辑试剂需提交审批后生效（审批流程见 §4.1.5）
- 修改 tests_per_kit 或 price_per_unit 时弹窗底部红色告警提示
- 试剂存在"待审批"变更时行背景高亮（浅黄色）

---

#### 4.1.5 审批流程

试剂目录的编辑操作（新增/修改/删除/停用）均需经过审批才能生效。这是一期"变更管控"理念的核心落地——任何对基础数据的变更都有迹可循、可追溯。

##### 4.1.5.1 审批状态机

```
                    ┌──────────┐
                    │  DRAFT   │  ← 保存草稿（用户自行编辑中）
                    └────┬─────┘
                         │ 提交审批 (submitApproval)
                         ▼
                    ┌──────────┐
            ┌───────│ PENDING  │───────┐
            │       └────┬─────┘       │
            │ 审批通过    │   审批驳回   │
            │ (approve)  │  (reject)   │
            ▼            │            ▼
     ┌──────────┐        │     ┌──────────┐
     │ APPROVED │        │     │ REJECTED │──→ 用户重新编辑 → PENDING
     └──────────┘        │     └──────────┘
            │            │
            │ 撤销审批    │
            │ (undo)     │
            └─────┬──────┘
                  ▼
            回到 PENDING
```

**状态说明**：

| 状态 | 含义 | 数据可见性 | 审批面板可见 |
|------|------|-----------|-------------|
| `DRAFT` | 用户保存但未提交审批 | 仅编辑者可见（列表显示"草稿"） | 不显示在审批面板 |
| `PENDING` | 已提交，等待主任审批 | 全员可见（列表显示"待审批"，黄色高亮） | 显示在「变更审批」Tab |
| `APPROVED` | 审批通过，变更生效 | 全员可见（列表显示"已批准"） | 移入「审批历史」 |
| `REJECTED` | 审批驳回，需修正 | 全员可见（列表显示"已驳回"，红色高亮） | 移入「审批历史」 |

##### 4.1.5.2 审批权限

| 角色 | 可提交审批 | 可审批 | 可撤销自己的审批决定 |
|------|-----------|--------|-------------------|
| 系统管理员 | ✅ | ✅ | ✅ |
| 检验科主任 | ✅ | ✅ | ✅ |
| 采购员 | ✅ | ❌ | ❌ |
| 库管员 | ✅ | ❌ | ❌ |
| 检验技师 | ❌（只读目录） | ❌ | ❌ |

> **一期规则**：审批人为检验科主任（单一审批节点，不设多级）。二期引入 Camunda 工作流后支持多级审批。

##### 4.1.5.3 审批记录数据结构

每次提交审批时，系统在 `catalog_approval_log` 表写入一条记录：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键（雪花ID） |
| `reagent_id` | BIGINT | 关联试剂 ID |
| `change_type` | ENUM | CREATE / UPDATE / DELETE / DISABLE |
| `field_changes` | JSON | 变更字段的 old→new 映射，如 `{"tests_per_kit":{"old":200,"new":180},"stability_days_after_open":{"old":30,"new":21}}` |
| `change_reason` | TEXT | 申请人填写的变更原因 |
| `submitter_id` | BIGINT | 申请人 ID |
| `submitter_name` | VARCHAR | 申请人姓名 |
| `reviewer_id` | BIGINT | 审批人 ID（待审批时为 NULL） |
| `reviewer_name` | VARCHAR | 审批人姓名（待审批时为 NULL） |
| `review_status` | ENUM | PENDING / APPROVED / REJECTED |
| `review_comment` | TEXT | 审批意见（驳回时必填） |
| `submitted_at` | DATETIME(3) | 提交时间 |
| `reviewed_at` | DATETIME(3) | 审批时间 |

##### 4.1.5.4 审批影响分析提示

审批通过前，系统对关键字段变更进行影响分析并在审批卡片上展示：

| 变更字段 | 影响范围 | 提示文案 |
|---------|---------|---------|
| `material_type` | 全流程行为 | "⚠️ 物料类型变更为 {new_type}，将影响五码映射要求、验收方式、消耗流程。请确认变更正确" |
| `tests_per_kit` | 效益分析·成本计算 | "⚠️ 此变更将影响效益分析的成本计算，历史报表数据不会回溯调整" |
| `price_per_unit` | 效益分析·成本计算 | "⚠️ 此变更将影响效益分析的成本计算" |
| `device_bind_mode` | 自动审核·规则跳过 | "⚠️ 切换为柔性关联后，该试剂自动审核将跳过设备校准规则(R-BATCH-004)" |
| `stability_days_after_open` | 批次规则·R-BATCH-002 | "⚠️ 此变更将影响开瓶超时拦截规则的判断阈值" |
| `min_stock_level` | 库存预警 | "⚠️ 此变更将影响库存预警触发阈值" |

**★ UDI 完整性检查（审批提交时自动触发）：**

| 条件 | 级别 | 审批卡片显示 |
|------|------|------------|
| material_type ∈ {REAGENT, CALIBRATOR, QC_MATERIAL} 且 udi_di IS NULL | 🔴 **阻断告警** | "⚠️ 该物料类型要求 UDI-DI，当前为空。审批通过后该物料将进入 UDI 待补全清单，采购订单生成将被阻断。建议驳回要求补全后再提交" |
| material_type ∈ {REAGENT, CALIBRATOR, QC_MATERIAL} 且 udi_di 已填但五码映射有字段缺失 | 🟡 **警告** | "⚠️ 五码映射不完整（{缺失字段列表}），建议补全。不阻断审批，但验收时可能无法自动匹配" |
| material_type ∈ {CONSUMABLE, OTHER} 且 udi_di 已填 | 🟢 **提示** | "ℹ️ 该物料类型不需要 UDI，已填的 UDI-DI 将保留但不参与验收流程。如需清除请返回编辑" |

> 审批人可据此做出更精准的判断，避免"盲批"。UDI 缺失的阻断告警不强制驳回——审批人可在有充分理由时通过（如：试剂尚未取得 UDI 但临床急需建目录），但该物料将自动进入 §4.1.7 UDI 待补全清单。

---

#### 4.1.6 一期数据表补充

M01 模块一期在 `reagent_catalog` 表基础上，增加以下字段（相对全本 PRD 的补充）：

```sql
ALTER TABLE reagent_catalog ADD COLUMN material_type ENUM('REAGENT','CALIBRATOR','QC_MATERIAL','CONSUMABLE','OTHER') NOT NULL DEFAULT 'REAGENT' COMMENT '物料类型·决定全流程行为';
ALTER TABLE reagent_catalog ADD COLUMN device_bind_mode ENUM('FORCE','FLEXIBLE') NOT NULL DEFAULT 'FORCE' COMMENT '设备关联策略';
ALTER TABLE reagent_catalog ADD COLUMN approval_status ENUM('DRAFT','PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态';
ALTER TABLE reagent_catalog ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记';
ALTER TABLE reagent_catalog ADD COLUMN lead_time_days INT NOT NULL DEFAULT 7 COMMENT '采购提前期(天)';
```

> **material_type 说明**：该字段为 **V1.1 新增**，是一期所有下游模块（申领、采购、验收、消耗、效益分析）的分流依据。存量数据迁移时默认全部设为 `REAGENT`，管理员需逐条确认并修正。

新增审批日志表：

```sql
CREATE TABLE catalog_approval_log (
    id BIGINT PRIMARY KEY,
    reagent_id BIGINT NOT NULL,
    change_type ENUM('CREATE','UPDATE','DELETE','DISABLE') NOT NULL,
    field_changes JSON COMMENT '变更字段old→new',
    change_reason TEXT,
    submitter_id BIGINT NOT NULL,
    submitter_name VARCHAR(64) NOT NULL,
    reviewer_id BIGINT,
    reviewer_name VARCHAR(64),
    review_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    review_comment TEXT,
    submitted_at DATETIME(3) NOT NULL,
    reviewed_at DATETIME(3),
    INDEX idx_reagent_id (reagent_id),
    INDEX idx_review_status (review_status, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试剂目录审批日志';
```

---

#### 4.1.7 ★ UDI 待补全清单（反差与修正机制）

> **设计意图**：material_type 为 REAGENT/CALIBRATOR/QC_MATERIAL 的物料，若 UDI-DI 为空或五码映射不完整，系统自动将其纳入此清单进行集中追踪。这是解决"本应有 UDI 但未填"的核心反差面板。

##### 4.1.7.1 入口与页面组件

**入口**：试剂目录页面 Tab 栏第 4 个 Tab：「⚠️ UDI 待补全」

```
UDI 待补全面板 (tab-panel#cpanel-udi-backlog)
│
├── 统计汇总条 (stat-summary, 渐变红底 flex)
│   ├── 🔴 阻断入库: N 条（已存在关联 PO）
│   ├── 🟡 待补全: N 条（尚未生成 PO）
│   └── 📊 总计: N 条
│
├── 筛选工具栏
│   ├── 类型筛选: 全部 / REAGENT / CALIBRATOR / QC_MATERIAL
│   ├── 影响等级: 全部 / 🔴阻断 / 🟡预警
│   └── 搜索框: 模糊匹配 院内编码 / 物料名称
│
└── UDI 待补全表格 (sticky header)
    ├── 列: [☑] [物料名称(主行+规格副行)] [院内编码] [类型标签] [创建时间] [影响等级] [关联PO数] [操作]
    ├── 影响等级:
    │   ├── 🔴 阻断入库: 已存在关联采购订单(PO)且未收货 → "该物料有在途采购，到货后无法扫码验收"
    │   └── 🟡 预警: material_type 要求 UDI 但尚未生成采购订单 → "须在首次采购前补全"
    ├── 操作列:
    │   ├── [✏️ 补全 UDI] → 弹出 UDI 补全弹窗（定位到五码映射编辑区）
    │   ├── [📧 通知供应商] → 自动生成 UDI 缺失清单 PDF → 邮件发送至绑定供应商
    │   └── [🔄 更正类型] → 若用户误选 REAGENT 但实际为 CONSUMABLE，快速更正 material_type
    └── 批量操作:
        ├── [☑ 全选] [📥 批量导入 UDI 映射 (Excel)]
        └── [📧 批量通知供应商]
```

##### 4.1.7.2 UDI 补全弹窗

```
UDI 补全弹窗 (modal-overlay#udiFillModal, 640px)
└── 弹窗容器
    ├── 标题栏: "✏️ 补全 UDI 信息 — {物料名称}"
    ├── 内容区
    │   ├── 物料信息只读行: 院内编码 | 名称 | 规格 | 厂家 | 类型
    │   ├── 五码映射编辑区 (form-grid 2列):
    │   │   ├── UDI-DI (input, 必填, GS1/MA格式校验)
    │   │   ├── GTIN (input, 选填)
    │   │   ├── 医保编码 (input, 选填, 27位)
    │   │   ├── NMPA注册证编号 (input, 必填)
    │   │   ├── 院内HIS编码 (disabled, 自动带入)
    │   │   └── 映射状态 (disabled, 自动变更为"已映射")
    │   └── 影响解除提示 (绿色背景 card):
    │       └── 若该物料之前有阻断级影响: "✅ 补全后 PO-xxxx 的扫码验收入口将自动恢复"
    └── 底部: [取消] [💾 仅保存] [✅ 保存并解除阻断]
```

##### 4.1.7.3 修正闭环的三个入口

| 入口 | 触发方式 | 适用场景 |
|------|---------|---------|
| **A — 目录列表行内** | 试剂列表行末「✏️ 补全 UDI」按钮（仅 udi_di IS NULL 时显示） | 浏览目录时发现单条缺失，随手补全 |
| **B — UDI 待补全面板集中处理** | Tab 4 中的「✏️ 补全 UDI」或「📥 批量导入」 | 批量排查时集中修正 |
| **C — 供应商协同** | UDI 待补全面板中的「📧 通知供应商」 | 多条目缺失且内部无法补全，反向推动供应商提供 UDI 码包数据 |

##### 4.1.7.4 联动阻断规则

| 阻断点             | 条件                                                                | 行为                                                           |
| --------------- | ----------------------------------------------------------------- | ------------------------------------------------------------ |
| **采购订单生成**（M04） | material_type ∈ {REAGENT,CALIBRATOR,QC_MATERIAL} 且 udi_di IS NULL | 「生成采购订单」按钮禁用，Tooltip: "该物料缺少 UDI-DI，请先在目录中补全"；允许「存草稿」暂存      |
| **验收入库**（M04）   | 同上条件，仅对已到货的 PO 行                                                  | 扫码验收入口不可用（灰色），仅显示「📝 手工登记」通道，入库后批次状态 = `PENDING_UDI`（见 §7.4） |

> **一期落地判断**：UDI 待补全面板为纯前端 Tab + 数据库查询，无外部依赖，一期可全部实现。供应商邮件通知依赖邮件服务，一期可用「复制清单」按钮代替——将 UDI 缺失清单复制到剪贴板，用户手动粘贴到邮件中。

---

### 4.2 M03 证照管理

一期实现供应商证照全生命周期电子化管理，包括：证照上传归档、明细查看、附件预览、效期预警、证照异常自动冻结采购（LIC-02 规则联动）。证照管理是采购流程的前置准入控制——证照失效则采购冻结。

#### 4.2.1 功能操作描述

##### 4.2.1.1 证照列表浏览与筛选

**入口**：侧边栏 → 证照管理

**页面加载行为**：
1. 顶部 KPI 统计卡加载：证照总数、有效期内、即将到期(≤30天)、已过期/冻结
2. 两个图表区渲染：证照效期分布饼图 + 未来3个月到期时间轴柱状图
3. 证照清单表格按「到期紧迫度」降序排列：已过期排最前→即将到期→有效
4. 每行显示：供应商、证照类型（带颜色标签）、证号、关联试剂、日期区间、剩余天数、附件数、状态、操作

**工具栏**（`toolbar-card` 突出卡片）：
- 搜索框：模糊匹配 供应商名称 / 证号 / 关联试剂
- 类型下拉：全部 / 产品注册证 / 生产许可证 / 经营许可证 / GMP证书 / GSP证书
- 状态下拉：全部 / 有效 / 即将到期 / 已过期 / 已冻结
- 操作按钮：📤 上传证照 (btn-primary) / 📥 批量导入 / 📤 导出报表

##### 4.2.1.2 证照明细查看（核心交互）

**触发**：点击证照清单中任意行，或点击行末「👁️」按钮

**弹窗结构**（Modal 960px 宽）：

| 区域 | 内容 | 数据来源 |
|------|------|---------|
| **证照基本信息** | 供应商、证照类型（颜色标签）、证号、发证机关、发证日期、有效期至（红/黄/绿色区分）、当前状态、数据更新时间 | `supplier_license` 表 |
| **关联试剂** | 胶囊列表，显示试剂名称 + 院内编码；点击可跳转至试剂目录详情 | `reagent_catalog` 表 JOIN |
| **附件档案** | 文件列表，每行显示：类型图标（📄PDF/🖼️图片）、文件名、大小、页数、上传时间/人；每行操作：「👁️ 预览」「📥 下载」 | MinIO 对象存储 |
| **有效期变更记录** | 时间线（timeline）展示：初始登记、续证、变更等历史事件 | `license_change_log` 表 |

**操作按钮**（Modal Footer）：
- [关闭] — 返回列表
- [✏️ 编辑] — 修改证照元数据（需审批）
- [📤 上传新证照] — 续证流程入口
- [🚫 冻结证照] — 手动冻结（二次确认），触发 LIC-02 规则

##### 4.2.1.3 附件预览

**触发**：点击证照明细中附件行的「👁️ 预览」按钮

**行为**：
1. 弹出附件预览弹窗，800px 宽
2. 预览区显示：文件图标(80px)、文件名、文件元信息（类型/大小/页数）
3. **一期实现**：展示文件占位预览（因私有化部署无公网 CDN），点击「📥 下载原文件」从 MinIO 拉取原始文件在浏览器新窗口打开
4. **二期演进**：PDF.js 内嵌渲染 + 图片灯箱

**一期附件存储方案**：
- 存储：MinIO（Docker Compose 部署，与主应用同机）
- 访问：通过 Nginx 反向代理 MinIO，生成预签名 URL（有效期 30 分钟）
- 格式限制：PDF / JPG / PNG，单文件 ≤ 20MB
- 组织：按 `{supplier_id}/{license_id}/{filename}` 路径存储

##### 4.2.1.4 上传证照

**触发**：点击「📤 上传证照」按钮

**流程**：
1. 选择供应商（下拉搜索）→ 选择证照类型 → 填写证号、发证机关、有效期
2. 拖拽或点击上传证照扫描件 PDF/JPG
3. 文件上传至 MinIO，返回 object_key 写入 `supplier_license` 表
4. 系统自动识别有效期并计算预警时间节点
5. 若为续证（同一证号已有记录），系统提示"是否覆盖旧证照？旧证照将归档至历史记录"

##### 4.2.1.5 效期预警与 LIC-02 规则联动

**预警时间节点**：

| 时间节点 | 触发动作 | 推送对象 |
|---------|---------|---------|
| 到期前 90 天 | 系统标记"关注"，列表显示绿色 | 采购员 |
| 到期前 30 天 | 标记"即将到期"（黄色），推送消息至采购员 + 主任 | 采购员 + 科主任 |
| 到期前 7 天 | 标记"紧急"（橙色），每日推送 | 采购员 + 科主任 + 供应商联系人 |
| 到期当天 | **自动触发 LIC-02 规则**：冻结该供应商所有采购流程；列表标记"已过期/冻结"（红色） | 全部相关角色 |

**解冻流程**：上传新证照 → 主任审批通过 → 自动解冻 → 恢复采购。

---

#### 4.2.2 UI 设计要点

**页面布局**：
- 顶部工具栏：搜索框（供应商/证号/关联试剂）+ 类型筛选 + 状态筛选 + 上传/批量导入/导出按钮
- KPI 统计卡（4 列）：证照总数、有效期内、即将到期(≤30天)、已过期/冻结
- 图表双栏：证照效期分布饼图 + 未来 3 个月到期时间轴柱状图
- 证照清单表格：供应商、证照类型(颜色标签)、证号、关联试剂、有效期至、剩余天数、附件数、状态、操作
- 行背景按状态着色：已过期=淡红、即将到期=淡黄、有效=默认

**证照明细弹窗**（960px 宽，4 个区域）：
- 证照基本信息（2×4 网格）
- 关联试剂胶囊列表（可点击跳转至试剂目录）
- 附件档案列表（文件类型图标 + 预览/下载按钮）
- 有效期变更记录（时间线展示）

**附件预览**：一期使用占位预览 + 「下载原文件」按钮（MinIO 预签名 URL）。PDF.js 内嵌渲染放二期。

**关键交互**：
- 点击表格行打开证照明细弹窗
- 证照过期自动触发 LIC-02 规则冻结采购（见 §4.2.1.5）
- 上传新证照（续证）→ 主任审批 → 自动解冻

---

#### 4.2.3 业务流 — 证照全生命周期

```
供应商建档
    │
    ▼
上传证照 (openLicenseUpload)
    │  必填：供应商、证照类型、证号、发证机关、有效期
    │  附件：PDF/JPG 扫描件 → MinIO
    │
    ▼
证照生效 (status = ACTIVE)
    │  关联试剂自动匹配（通过 supplier_id）
    │
    ├──→ [日常监控]
    │       │
    │       ├── 到期前90天 → 标记"关注"
    │       ├── 到期前30天 → 预警推送（采购员 + 主任）
    │       ├── 到期前7天  → 紧急推送（每日）
    │       │
    │       └── 到期当天
    │               │
    │               ▼
    │          触发 LIC-02 规则
    │               │
    │               ├── 证照状态 → EXPIRED
    │               ├── 该供应商所有试剂采购流程冻结
    │               └── 规则引擎 R-LNK-001 同步拦截该供应商关联试剂的结果审核
    │
    ├──→ [续证流程]
    │       │
    │       ▼
    │   上传新证照（同证号）
    │       │
    │       ▼
    │   主任审批
    │       │
    │       ├── 通过 → 证照状态恢复 ACTIVE → 自动解冻采购 → R-LNK-001 解除拦截
    │       └── 驳回 → 维持 EXPIRED → 通知申请人补正
    │
    └──→ [手动冻结]
            │
            ▼
        主任/管理员主动冻结（如供应商出现质量问题）
            │
            ▼
        证照状态 → FROZEN → 触发 LIC-02（同过期逻辑）
```

#### 4.2.4 一期数据表

```sql
-- supplier_license 证照主表
CREATE TABLE supplier_license (
    id BIGINT PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    license_type ENUM('PRODUCT_REG','PRODUCTION','OPERATION','GMP','GSP','OTHER') NOT NULL,
    cert_no VARCHAR(128) NOT NULL,
    issuing_authority VARCHAR(256),
    issue_date DATE NOT NULL,
    expire_date DATE NOT NULL,
    status ENUM('ACTIVE','EXPIRING','EXPIRED','FROZEN') NOT NULL DEFAULT 'ACTIVE',
    -- MinIO 附件
    attachments JSON COMMENT '[{"name":"...","object_key":"...","size":...,"type":"pdf/img","uploaded_at":"...","uploaded_by":"..."}]',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    INDEX idx_supplier (supplier_id),
    INDEX idx_status_expire (status, expire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- license_change_log 变更记录表
CREATE TABLE license_change_log (
    id BIGINT PRIMARY KEY,
    license_id BIGINT NOT NULL,
    change_type ENUM('CREATE','RENEW','UPDATE','FREEZE','UNFREEZE') NOT NULL,
    old_expire_date DATE,
    new_expire_date DATE,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(64),
    note TEXT,
    created_at DATETIME(3) NOT NULL,
    INDEX idx_license (license_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 4.3 M04 采购管理 + 验收（扫码验收 + 数量验收）

> **核心业务模型（区别于传统 ERP）**：二级库（科室库/组库）向中心库申领物料（混合申领：试剂 + 耗材 + 其他） → 中心库按库存拣选发货 → 库存不足部分自动生成缺货单 → 系统自动按 (material_type, supplier_id) 拆分生成 PO → 试剂类走扫码验收 / 耗材类走数量验收 → 入库 → 补发至申领科室。
> 一期扫码验收支持 GS1 DataMatrix / Code128 / HIBC 三种码制；数量验收适用于 CONSUMABLE / OTHER 类型。

#### 4.3.1 功能概述

M04 是一期进销存闭环的**进货入口**。采购订单有三种生成方式——**缺货驱动**（被动）、**人工采购**（主动预判）、**智能补货**（数据驱动）。★ **V1.1 新增**：缺货单 → PO 阶段系统自动按供应商 + 验收方式拆单，试剂和耗材不会混入同一张 PO。

| 阶段 | 子功能 | 操作角色 | 输入 | 产出 | 下游模块 |
|------|--------|---------|------|------|---------|
| **院内调配** | 申领管理 | 检验技师（发起）、中心库管（审核拣货） | 物料名称/规格/数量/科室/紧急程度（★ 支持试剂+耗材混合申领） | 全部发货 / 部分发货+缺货单 / 全额缺货单 | → M06 三级库（已发货部分） |
| **院内调配** | 缺货单管理 | 运营人员（发起采购） | 中心库库存不足自动生成（★ 缺货明细带 material_type 标签） | 缺货单 → ★ 自动拆单 → 驱动对外采购 | → 多张采购订单（按供应商+验收方式分组） |
| **对外采购** | 采购订单（缺货驱动） | 采购员 | 缺货单 → 系统自动拆单 → 选择供应商 → 生成PO | 正式采购订单（PO-），★ 每张PO标注验收方式 | → 扫码验收 / 数量验收 |
| **对外采购** | 采购订单（人工采购） | 采购员 | 主动发起，选择物料+供应商+数量（★ 试剂/耗材分别建PO） | 独立采购订单（PO-，不关联缺货单） | → 扫码验收 / 数量验收 |
| **对外采购** | 采购订单（智能补货） | 采购员/系统 | 中心库 N 日出库量 → 日均消耗 → 安全库存计算 | 按供应商分组批量生成采购订单（★ 自动区分类型） | → 扫码验收 / 数量验收 |
| **对外采购** | **扫码验收** | 库管员 | 实物到货 + UDI码扫描（★ 仅 REAGENT/CALIBRATOR/QC_MATERIAL） | 入库记录 + UDI状态变更(RECEIVED→IN_STOCK) | → M06 三级库 → 补发至申领科室 |
| **对外采购** | **★ 数量验收**（V1.1 新增） | 库管员 | 实物到货 + 手工核对品名/规格/数量（CONSUMABLE/OTHER） | 入库记录 + 库存直接增加 | → M06 三级库 → 补发至申领科室 |

**业务目标**：
- 院内调配优先：优先消耗中心库已有库存，减少不必要的对外采购
- 缺货自动转单：中心库库存不足时，缺货部分自动生成缺货单，不阻塞已有库存的发货
- 部分发货能力：库存可满足部分申领时，先发满足的部分；剩余部分跟踪缺货补货进度
- **人工采购**：支持不依赖缺货单的独立采购，适用于预判性补货、批量备货
- **智能补货**：基于中心库历史出库量，自动计算安全库存（默认30天）并建议采购量，按供应商分组批量生成PO
- ★ **混合申领 + 自动拆单**：检验技师一张申领单覆盖试剂+耗材，系统在后端按供应商+验收方式自动拆分 PO，采购和验收环节不混淆
- 验收扫码识别率 ≥99.9%（GS1/Code128/HIBC 三码制兼容）
- 采购全链路可追溯：任一入库批次可反向追溯至申领人、中心库审核人、缺货单、供应商、订单

**与其他模块的关联**：
- **M01**：申领时从物料目录选择物料，自动带出规格/注册证/UDI-DI（按 material_type 动态显示）
- **M03**：生成采购订单前校验供应商证照状态（R-LNK-001 规则联动）。★ CONSUMABLE 豁免注册证校验
- **M06**：中心库拣货发货后扣减库存；验收后入库中心库增加库存
- **X1 UDI 追溯**：扫码验收写入 UDI 状态机第一条记录（RECEIVED → IN_STOCK）；数量验收不进入 UDI 状态机

---

#### 4.3.2 页面设计要点

三个页面为侧边栏同级独立菜单项，通过 `navigateTo(pageId)` 互相跳转：

**申领管理页面**：
- KPI 统计卡（4 列）：待审核 / 拣选中 / 已完成 / 缺货待采
- 申领单列表：申领单号、科室、物料、规格、申领量/发货量/缺货量、状态标签
- 行背景按状态区分：待审核=淡黄、拣选中/部分发货=淡蓝、缺货待采=淡红
- 操作：查看、审核（中心库管）、查看缺货单（跳转至缺货单页面）

**缺货单页面**：
- KPI 统计卡（4 列）：待采购 / 采购中 / 已完成 / 缺货品种
- 缺货单列表：缺货单号(SH-)、来源申领(链接)、科室、物料、缺货量、预估金额、建议供应商
- 操作：查看、生成采购单、查看订单（跳转至采购管理页面）

**采购管理页面**：
- KPI 统计卡（4 列）：待收货 / 部分收货 / 已完成 / 已取消
- 采购订单列表：订单号(PO-)、来源(缺货单链接/✋人工/🔄智能补货)、验收方式(📱扫码/📝数量/混合)、供应商、收货进度(进度条)
- 操作栏含：新建采购单、智能补货建议、查看缺货单
- 验收入口按 PO 验收方式动态渲染：纯试剂=扫码验收、纯耗材=数量验收、混合=分项验收弹窗

**申领明细弹窗**（1060px）：双栏信息 + 汇总条 + 筛选工具栏 + 分页表格。支持按组别分组折叠视图。

**新建申领弹窗**（1040px）：双模式 —— 逐条添加（搜索卡片式试剂选择器，按 SKU 粒度定位）+ 批量勾选（全量目录表格）。支持模板快速填充。

**智能补货弹窗**（1100px）：选择分析周期（7/14/30 天），系统自动计算日均消耗 + 可支撑天数 + 建议采购量，按供应商分组批量生成 PO。

**关键交互**：
- 生成采购单前校验供应商证照状态（LIC-02 规则联动）
- 智能补货：建议采购量 = 日均消耗 × 30 天 - 当前库存
- 试剂选择器按 23 SKU 粒度定位（名称+规格+方法学+厂家+注册证号），库存颜色：≥20=绿 / 0<库存<20=黄+预警 / 0=红+缺货


##### 4.3.2.7 ★ 自动拆单规则（V1.1 新增）

> **设计意图**：检验技师一张申领单可混合申领试剂+耗材，但采购和验收阶段必须分流。系统在缺货单 → 生成 PO 时自动按 (supplier_id, acceptance_method) 拆单。

**拆单流程：**

```
缺货单 SH-YYYYMMDD-NNN（明细含多种 material_type）
  │
  │  系统遍历每条缺货明细 → 从 reagent_catalog 取 material_type
  │
  ├── Group 1: material_type IN (REAGENT, CALIBRATOR, QC_MATERIAL)
  │      → acceptance_method = SCAN（扫码验收）
  │      → 再按 supplier_id 分子组
  │        ├── 供应商甲 → 生成 PO-A（扫码验收PO）
  │        └── 供应商乙 → 生成 PO-B（扫码验收PO）
  │
  ├── Group 2: material_type IN (CONSUMABLE, OTHER)
  │      → acceptance_method = MANUAL（数量验收）
  │      → 再按 supplier_id 分子组
  │        ├── 供应商甲 → 若与 Group 1 同供应商 → 独立 PO-C（数量验收PO）
  │        │           （不合并：同一供应商但验收方式不同，必须拆成两张 PO）
  │        └── 供应商丙 → 生成 PO-D（数量验收PO）
  │
  └── 结果: 一张缺货单 → N 张 PO（按 supplier + acceptance_method 严格拆分）
```

**拆单规则明细：**

| 规则编号 | 逻辑 |
|---------|------|
| **SPLIT-01** | 不同 supplier_id → 不同 PO（供应商不能合并） |
| **SPLIT-02** | 不同 acceptance_method → 不同 PO（即便是同一供应商，扫码验收和数量验收也不合并） |
| **SPLIT-03** | 同一 supplier + 同一 acceptance_method → 同一 PO（可合并） |
| **SPLIT-04** | CONSUMABLE / OTHER 不触发证照冻结拦截（R-LNK-001 仅在试剂类 PO 生效） |

**采购员视角**：点击「📦 从缺货单生成采购订单」后，系统自动弹窗展示拆分结果（只读），采购员确认供应商和预计到货日期后逐张生成 PO。

##### 4.3.2.8 ★ 混合 PO 验收方式选择弹窗（V1.1 新增）

```
混合PO验收入口选择 (modal-overlay#mixedAcceptModal, 480px)
└── 弹窗容器
    ├── 标题栏: "PO-YYYYMMDD-NNNN · 分项验收"
    ├── 内容区
    │   ├── 试剂类行 (N项) — 📱 扫码验收
    │   │   └── [📱 进入扫码验收] → 打开 PDA UDI 扫描界面
    │   └── 耗材/其他行 (M项) — 📝 数量验收
    │       └── [📝 进入数量验收] → 打开数量验收界面
    └── 底部: [取消]
```

> **注意**：混合 PO 仅在极少数情况出现（人工采购时操作员未正确分类）。规范流程应确保 SPLIT-01~03 将 PO 分为纯扫码或纯数量验收。此弹窗为兜底。

---

#### 4.3.3 业务流 — 采购全链路

```
检验技师（二级库）发起申领 (openNewReqModal)
    │
    │  填写：申领科室（二级库）、紧急程度
    │  选择试剂：通过搜索卡片式选择器按 SKU 粒度定位
    │    （名称+规格+方法学+厂家+注册证号），支持模板/批量勾选
    │  目标仓库：中心库（默认，不可更改）
    │  申领单号自动生成: REQ-YYYYMMDD-NNN
    │
    ▼
申领单提交 (status = PENDING_REVIEW)
    │
    │  推送审核任务至中心库管理员
    │
    ├─→ [中心库管审核 + 库存检查]
    │       │
    │       │  检查中心库每种试剂的当前库存（stock_on_hand）
    │       │
    │       ├── 库存充足 (stock_on_hand ≥ qty_req)
    │       │       │
    │       │       ▼
    │       │   全部发货 (status = FULFILLED)
    │       │       qty_fulfilled = qty_req
    │       │       qty_shortage = 0
    │       │       中心库库存扣减 → 二级库库存增加
    │       │       ✅ 申领单完成
    │       │
    │       ├── 库存部分满足 (0 < stock_on_hand < qty_req)
    │       │       │
    │       │       ▼
    │       │   部分发货 (status = PARTIAL_FULFILLED)
    │       │       qty_fulfilled = stock_on_hand
    │       │       qty_shortage = qty_req - stock_on_hand
    │       │       有库存部分先发货 → 二级库
    │       │       │
    │       │       ▼
    │       │   短缺部分自动生成缺货单 (SH-YYYYMMDD-NNN)
    │       │       关联来源申领单号
    │       │       status = PENDING_PROCUREMENT
    │       │
    │       └── 中心库零库存 (stock_on_hand = 0)
    │               │
    │               ▼
    │           全额转缺货单 (status = SHORTAGE_PENDING)
    │               qty_fulfilled = 0
    │               qty_shortage = qty_req
    │               → 自动生成缺货单
    │
    ├─→ [运营人员处理缺货单]
    │       │
    │       │  查看缺货单列表（按紧急程度排序）
    │       │  系统自动推荐供应商（基于M01试剂目录的供应商绑定）
    │       │
    │       ▼
    │   生成采购订单 (createPOfromShortage) ← 【路径A：缺货驱动】
    │       │
    │       │  选择供应商、确认数量/价格、预计到货日期
    │       │  生成正式 PO: PO-YYYYMMDD-NNNN
    │       │  缺货单状态 → PURCHASING
    │       │
    │       ├─→ [验收入库] ...
    │
    ├─→ [采购员人工发起采购] ← 【路径B：人工采购】
    │       │
    │       │  点击「＋ 新建采购单」→ openManualPO()
    │       │  不依赖缺货单，独立选择试剂+供应商+数量
    │       │  生成 PO-，来源标记为"人工采购"
    │       │
    │       └─→ [验收入库] ...
    │
    └─→ [系统智能补货建议] ← 【路径C：数据驱动】
            │
            │  点击「🔄 智能补货建议」→ openSmartReplenish()
            │  选择分析周期（7/14/30天/自定义）
            │  系统计算：日均消耗 = 周期出库量 / 天数
            │            可支撑天数 = 当前库存 / 日均消耗
            │            建议采购量 = 日均消耗 × 30 - 当前库存
            │  按供应商分组 → 批量生成 PO-
            │
            └─→ [验收入库] ...

    ┌────────────────────────────────────────┐
    │ 三条路径汇总 → 验收入库 → 补发申领科室   │
    └────────────────────────────────────────┘
    │       ▼
    │   【对外采购 → 供应商发货】
    │       │
    │       ▼
    │   【扫码验收 ← M05 衔接】
    │       │
    │       ├── 实物到货
    │       ├── PDA 扫 UDI 码 (GS1/Code128/HIBC)
    │       ├── 校验：码制识别 → 五码匹配 → 证照校验 → 数量核对
    │       ├── 验收通过 → 入库中心库（UDI: RECEIVED → IN_STOCK）
    │       ├── 订单状态 → PARTIAL_RECEIVED / COMPLETED
    │       ├── 缺货单状态 → COMPLETED（全部验收后）
    │       └── 验收异常 → 触发 R-INB-001 规则
    │
    └─→ [补发至申领科室]
            │
            │  中心库补货到位后，通知原申领科室
            │  生成补发出库单 → 中心库 → 二级库
            │  申领单状态 → FULFILLED
            │
            ✅ 采购全链路闭环

全院采购追溯链:
  申领人(二级库) → 中心库审核人 → 缺货单 → 采购员 → 供应商 → PO号 →
  入库批号 → UDI码 → 补发出库 → 二级库接收 → 上机消耗
  任意节点均可正向追溯（申领→消耗）和反向追溯（消耗→申领）
```

---

#### 4.3.4 一期验收 DDL

> DDL 仅用于一期 MVP 关键表；完整 DDL 见全本 PRD。

```sql
-- 申领单主表（二级库 → 中心库）
CREATE TABLE purchase_requisition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    req_no VARCHAR(32) NOT NULL COMMENT '申领单号 REQ-YYYYMMDD-NNN',
    from_dept_id INT NOT NULL COMMENT '申领科室ID（二级库）',
    to_warehouse_id INT NOT NULL DEFAULT 1 COMMENT '目标仓库ID（中心库，默认1）',
    applicant_id INT NOT NULL COMMENT '申请人ID',
    urgency ENUM('NORMAL','URGENT','CRITICAL') DEFAULT 'NORMAL' COMMENT '紧急程度',
    status ENUM('DRAFT','PENDING_REVIEW','REVIEWED','PICKING','PARTIAL_FULFILLED','FULFILLED','SHORTAGE_PENDING','CANCELLED') DEFAULT 'DRAFT',
    reviewer_id INT COMMENT '中心库审核人ID',
    review_time DATETIME(3) COMMENT '审核时间',
    review_comment VARCHAR(500) COMMENT '审核意见',
    remark VARCHAR(1000) COMMENT '备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_req_no (req_no),
    INDEX idx_status (status),
    INDEX idx_from_dept (from_dept_id),
    INDEX idx_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申领单主表（二级库→中心库）';

-- 申领明细表（含库存实查 & 拣货结果）
CREATE TABLE purchase_requisition_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    req_id BIGINT NOT NULL COMMENT '申领单ID',
    line_no INT NOT NULL COMMENT '行号',
    reagent_id INT NOT NULL COMMENT '试剂ID (FK → M01)',
    specification VARCHAR(100) COMMENT '规格（自动填充）',
    qty_requested DECIMAL(10,2) NOT NULL COMMENT '申领数量',
    stock_on_hand DECIMAL(10,2) DEFAULT 0 COMMENT '中心库当前库存（审核时实查值）',
    qty_fulfilled DECIMAL(10,2) DEFAULT 0 COMMENT '实发数量（库存可满足部分）',
    qty_shortage DECIMAL(10,2) DEFAULT 0 COMMENT '缺货数量 = qty_requested - qty_fulfilled',
    unit_price DECIMAL(10,2) COMMENT '预估单价',
    amount DECIMAL(12,2) COMMENT '行金额',
    group_id INT COMMENT '检测组别ID',
    shortage_order_id BIGINT COMMENT '关联缺货单ID（如有短缺）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_req_id (req_id),
    INDEX idx_reagent (reagent_id),
    INDEX idx_shortage_order (shortage_order_id),
    FOREIGN KEY (req_id) REFERENCES purchase_requisition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申领明细表（含拣货结果）';

-- 缺货单主表（中心库库存不足 → 驱动对外采购）
CREATE TABLE shortage_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shortage_no VARCHAR(32) NOT NULL COMMENT '缺货单号 SH-YYYYMMDD-NNN',
    req_id BIGINT NOT NULL COMMENT '来源申领单ID',
    from_dept_id INT NOT NULL COMMENT '申领科室ID',
    status ENUM('PENDING_PROCUREMENT','PURCHASING','REPLENISHED','COMPLETED','CANCELLED') DEFAULT 'PENDING_PROCUREMENT',
    urgency ENUM('NORMAL','URGENT','CRITICAL') DEFAULT 'NORMAL',
    suggested_supplier_id INT COMMENT '建议供应商ID（基于M01绑定）',
    po_id BIGINT COMMENT '关联采购订单ID',
    created_by INT COMMENT '创建人ID（运营人员）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_shortage_no (shortage_no),
    INDEX idx_req_id (req_id),
    INDEX idx_status (status),
    FOREIGN KEY (req_id) REFERENCES purchase_requisition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺货单主表';

-- 缺货单明细表
CREATE TABLE shortage_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shortage_order_id BIGINT NOT NULL COMMENT '缺货单ID',
    line_no INT NOT NULL COMMENT '行号',
    req_item_id BIGINT NOT NULL COMMENT '来源申领明细ID',
    reagent_id INT NOT NULL COMMENT '试剂ID',
    qty_shortage DECIMAL(10,2) NOT NULL COMMENT '缺货数量',
    est_unit_price DECIMAL(10,2) COMMENT '预估单价',
    est_amount DECIMAL(12,2) COMMENT '预估金额',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (shortage_order_id) REFERENCES shortage_order(id),
    FOREIGN KEY (req_item_id) REFERENCES purchase_requisition_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺货单明细表';

-- 采购订单主表（由缺货单驱动）
CREATE TABLE purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_no VARCHAR(32) NOT NULL COMMENT '采购订单号 PO-YYYYMMDD-NNNN',
    shortage_order_id BIGINT NOT NULL COMMENT '来源缺货单ID',
    req_id BIGINT NOT NULL COMMENT '来源申领单ID（冗余，便于追溯）',
    supplier_id INT NOT NULL COMMENT '供应商ID',
    status ENUM('DRAFT','SENT','PARTIAL_RECEIVED','COMPLETED','CANCELLED') DEFAULT 'DRAFT',
    total_amount DECIMAL(14,2) COMMENT '订单总金额',
    order_date DATE NOT NULL COMMENT '下单日期',
    expected_arrival DATE COMMENT '预计到货日期',
    received_quantity DECIMAL(10,2) DEFAULT 0 COMMENT '已收货总数量',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_po_no (po_no),
    INDEX idx_shortage_order (shortage_order_id),
    INDEX idx_req_id (req_id),
    INDEX idx_supplier (supplier_id),
    INDEX idx_status (status),
    FOREIGN KEY (shortage_order_id) REFERENCES shortage_order(id),
    FOREIGN KEY (req_id) REFERENCES purchase_requisition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单主表（由缺货单驱动）';

-- 采购订单明细表
CREATE TABLE purchase_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_id BIGINT NOT NULL COMMENT '订单ID',
    line_no INT NOT NULL COMMENT '行号',
    reagent_id INT NOT NULL COMMENT '试剂ID',
    specification VARCHAR(100) COMMENT '规格',
    order_quantity DECIMAL(10,2) NOT NULL COMMENT '订购数量',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    amount DECIMAL(12,2) COMMENT '行金额',
    received_quantity DECIMAL(10,2) DEFAULT 0 COMMENT '已收货数量',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (po_id) REFERENCES purchase_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';
```

---

#### 4.3.5 ★ 数量验收（V1.1 新增 — CONSUMABLE / OTHER 专用）

> **适用条件**：PO 的 acceptance_method = MANUAL，或 PO 明细的 material_type ∈ {CONSUMABLE, OTHER}。数量验收不进入 UDI 状态机，不入 udi_status_log。

##### 4.3.5.1 数量验收页面组件

```
数量验收页面 (page#page-qty-inbound)
│
├── 页面标题栏 (page-title)
│   ├── 标题: "📝 数量验收"
│   └── 副标签: "耗材/其他类 · 手工核对"
│
├── PO 信息关联区 (info-card)
│   ├── 订单号: PO-YYYYMMDD-NNNN | 供应商: xxx
│   ├── 来源: 缺货单号 / ✋人工采购 / 🔄智能补货
│   └── 预计到货: YYYY-MM-DD
│
├── ★ 验收明细表格 (card, editable)
│   ├── 列: [#] [物料名称·规格·厂家] [类型标签] [订购数] [实收数(input)] [批号(input,选填)] [效期(input,选填)] [验收结果]
│   ├── 行内交互:
│   │   ├── 实收数: number input, 默认=订购数, 可修改
│   │   ├── 批号: text input (选填) — 仅当物料有厂商批号时填写
│   │   ├── 效期: date picker (选填)
│   │   └── 验收结果:
│   │       ├── ✅ 合格（默认）— 实收数 > 0
│   │       ├── ⚠️ 部分合格 — 实收数 < 订购数
│   │       └── ❌ 不合格 — 实收数 = 0（触发退货流程）
│   └── 汇总行: 共 N 项 | 实收合计 M | 合格 N1 / 部分 N2 / 不合格 N3
│
├── 拍照存证区 (card, 可选)
│   ├── [📷 拍照] [📁 上传图片] — 可选, 手套/口罩类建议拍照
│   └── 已上传缩略图预览 (grid 4列)
│
├── 备注区
│   └── textarea: "备注（如：包装破损、数量短少原因…）"
│
└── 底部操作栏
    ├── [取消]
    └── [✅ 确认验收] — 提交后: 库存直接增加 + 写入 consumption_record 表（类型标注 INBOUND）
```

##### 4.3.5.2 数量验收操作流程

```
库管员收到实物到货
  → 在采购订单列表点击对应 PO 的「📝 数量验收」按钮
  → 进入数量验收页面（系统预填 PO 全部明细）
  → 逐行操作：
     ├── 目视检查品名/规格/包装完整性
     ├── 手工录入实收数量
     ├── 如有厂商批号 → 手工录入（选填）
     ├── 如有有效期 → 手工录入（选填）
     └── 选择验收结果（合格/部分合格/不合格）
  → 可选：拍照存证
  → 填写备注
  → 提交确认
  → 系统处理：
     ├── 生成虚拟批次号: BATCH-CONS-YYYYMMDD-NNN（按到货日期自动生成）
     ├── 库存直接增加（不入 UDI 状态机）
     ├── 记录写入 consumption_record 表（type = INBOUND）
     ├── PO 收货进度更新
     └── 缺货单状态更新
```

##### 4.3.5.3 虚拟批次号规则

| 耗材子类 | 批号策略 | 示例 |
|----------|---------|------|
| 通用耗材（吸头/反应杯/采样管） | 到货日期作为虚拟批号，系统自动生成 | `BATCH-CONS-20260629-001` |
| 有批号耗材（手套/口罩） | 优先取手工录入的厂商批号，无则用虚拟批号 | 厂商喷码 或 `BATCH-CONS-20260629-002` |
| 消毒液/清洁剂 | 若有录入则取厂商批号+效期，无则虚拟批号 | 同上有/无差异 |

##### 4.3.5.4 数量验收业务规则

| 规则编号 | 内容 |
|---------|------|
| **QINB-01** | 数量验收不校验 UDI-DI、不进入 UDI 状态机、不写入 udi_status_log |
| **QINB-02** | 实收数默认=订购数，库管员可修改。若实收数 < 订购数，该行自动标记"部分合格"，差额转入"待补发" |
| **QINB-03** | 全部不合格行 → 触发退货流程（PO 行状态 → RETURNED），系统生成退货单据 |
| **QINB-04** | 数量验收不分配 FEFO 优先级（耗材不出现在 FEFO 推荐列表中） |
| **QINB-05** | 验收确认时间：无超时限制（区别于扫码验收的 INB-06 的 30 分钟规则）。耗材批量验收时间长 |
| **QINB-06** | CONSUMABLE 类验收时不校验产品注册证（手套不需要 NMPA 注册证） |

---

### 4.4 M06 三级库 + FEFO

沿用全本 PRD M06/M07。三级库层级、FEFO 出库推荐、库存预警。

**一期简化**：库位编码从四级 `A-03-02-05` 简化为二级 `库区-货架`，避免过度设计（中小医院用不到四级粒度）。

#### ★ 批号切换全流程管控（一期嵌入 M04→M06→M07）

> 这是 ISO 15189 6.6.3"新批号使用前应验证"的系统化落地。新批号入库不是孤立的仓库操作——它触发一条跨模块管控链。

| 规则 | 触发场景 | 一期实现动作 | 推送对象 |
|------|---------|-------------|---------|
| **INB-NEW** | M04扫码验收，同试剂已有旧批号在库 | 弹窗列出旧批号（批号/库位/剩余量/效期），估算旧批可支撑天数，建议FEFO先用旧的 | 库管员 + 组组长 |
| **INB-QC** | M04入库确认后 | 自动生成待办任务："新批号 `{new}` 需质控验证"；旧批可支撑 <3天时升级紧急 | 质控管理员 + 组组长 |
| **WH-TAIL** | 旧批号库存降至尾数线（≤2盒 或 ≤3天用量） | 预警："旧批号即将耗尽（剩余{x}盒，约{y}天），请确认新批号质控已完成" | 组组长 + 质控管理员 |
| **WH-SWITCH** | 旧批号库存=0 | 自动将旧批号状态→CONSUMED，新批号→活跃批号；Redis设备-批次映射缓存更新；自动审核流水线切换为新批号规则匹配 | 组组长 |
| **TRF-ASK** | 二级库向中心库请领时，旧批号≤尾数线且新批号在中心库 | 主动建议将新批号加入本次请领单（一键加入），避免断供 | 请领人 + 科主任审批可见 |
| **TRF-DEDUP** | 调拨批号与目标库已有批号不同 | 校验拦截："目标库该试剂已有批号 `{old}` 在库，请先消耗旧批号再调入新批号" | 库管员 |

> **一期落地判断**：6条规则均为**数据库查询+弹窗/消息推送**，无外部依赖，一期可全部实现。其中 WH-SWITCH 联动自动审核（批次耗尽→审核流水线切换）是"批次感知"特色的核心体现。

### 4.5 M08 上机与消耗管理（★ 自动审核的数据基础）

**这是一期最关键的模块——它既是库存管理，又是自动审核的数据来源。**

#### 4.5.1 核心操作：扫码上机

```
技师将 BATCH-2025A 装载到 AU5800-01 设备
  → 扫试剂 UDI 码 + 选设备
  → 系统记录：上机记录(device_id, batch_no, load_time, [unload_time])
  → 触发 Redis 缓存更新：device_id → [active_batches] 映射
  → 试剂状态 IN_STOCK → IN_USE
```

#### 4.5.2 消耗核销方式（**一期砍掉自动核销**）

| 方式 | 一期 | 说明 |
|---|---|---|
| 自动核销（设备上报吸样量） | ❌ 砍掉 | 设备不上报，落地不了 |
| 手动核销 | ✅ | 技师输入消耗量 |
| 批量核销 | ✅ | 按日/按班次批量确认 |

> **说明**：自动核销缺失会影响"试剂→患者"追溯精度，但不影响自动审核（审核只需要"设备×时间→批次"映射，不需要精确到患者）。这是经过权衡的取舍。

#### 4.5.3 ★ 设备关联模式可配置

> 不是所有试剂都跑在大型设备上——手工法、POCT、显微镜检查无法绑定设备。系统支持按试剂配置"强制关联"还是"柔性关联"。

| 模式 | 上机时设备字段 | 消耗时设备字段 | 审核流水线行为 | 适用场景 |
|------|--------------|--------------|--------------|---------|
| **强制关联**（默认） | 必填 | 必填 | L1+L2+L3 全部规则执行（含设备校准、设备→批次时间匹配） | 生化仪、化学发光仪等固定设备 |
| **柔性关联** | 可选（留空="未指定设备"） | 可选 | 跳过设备相关规则（R-BATCH-004），仅执行L1结果+L2中不依赖设备的批次规则；审核结果标注"柔性模式" | 手工法、镜检、POCT床旁 |

**配置粒度**：
- 试剂级：编辑单个试剂→"设备关联策略"下拉（强制/柔性）
- 组别级：检验组别→批量设置默认模式（如微生物组默认柔性、生化组默认强制）
- 全局级：系统配置→`force_device_bind`（新建试剂默认值）

**一期实现方式**：`reagent_catalog` 表新增字段 `device_bind_mode` (ENUM: FORCE / FLEXIBLE)，默认 FORCE。上机/消耗API根据此字段校验设备ID是否必填。柔性模式下消耗记录 `device_id` 为 NULL。

> **对自动审核的影响**：柔性模式下的结果跳过 R-BATCH-004（设备校准到期拦截），但L2的其他批次规则（效期/开瓶/异常标记）仍然执行。审核统计报表中"柔性模式"结果单独分组。

#### 4.5.4 ★ 耗材/其他领用消耗（V1.1 新增 — CONSUMABLE / OTHER 专用）

> **适用条件**：material_type ∈ {CONSUMABLE, OTHER}。此类物料不绑定设备、不走上机流程、不入 UDI 状态机。消耗方式只有"手动领用"。

##### 4.5.4.1 核心操作：直接领用

```
护士/技师从科室库领取吸头、手套等耗材
  → 在 PC/PDA 端进入「📝 领用消耗」界面
  → 选择物料 + 输入领用数量 + 选择用途
  → 提交 → 库存直接扣减
  → 写入 consumption_record 表（type = CONSUME, device_id = NULL）
```

##### 4.5.4.2 领用消耗页面组件

```
领用消耗页面 (page#page-consume-consumable)
│
├── 页面标题栏 (page-title)
│   ├── 标题: "📝 领用消耗"
│   └── 副标签: "耗材/其他类 · 直接领用扣减"
│
├── 领用信息区 (info-card)
│   ├── 领用科室: [生化组 ▼]
│   ├── 领用人: [张三] (自动取当前登录用户)
│   └── 日期: [2026-06-29] (默认今天)
│
├── 添加物料区
│   ├── [＋ 添加物料] → 弹出物料选择器（仅显示 CONSUMABLE + OTHER 类型）
│   └── 物料明细表格:
│       ├── 列: [#] [物料名称·规格] [类型标签] [当前库存(绿/黄/红)] [领用数(input)] [用途]
│       ├── 库存颜色: ≥20=绿 / 0<库存<20=黄+预警 / 库存=0=红+缺货
│       ├── 用途下拉: 日常消耗 / 批量检测 / 设备维护 / 科内领用 / 其他
│       └── 行操作: [✕ 删除]
│
├── 汇总条: 共 N 项 | 总领用数量 M
│
└── 底部操作栏
    ├── [取消]
    └── [✅ 确认领用]
```

##### 4.5.4.3 与试剂消耗的关键差异

| 维度 | 试剂 (REAGENT) | 耗材/其他 (CONSUMABLE/OTHER) |
|------|---------------|---------------------------|
| 上机操作 | 扫码上机（UDI + 设备资产码） | 无上机概念，直接领用 |
| 设备关联 | device_id 记录在 device_batch_load 表 | device_id = NULL |
| 状态流转 | IN_STOCK → IN_USE → CONSUMED | 库存直接扣减（无状态机） |
| UDI 日志 | 每次状态转移写 udi_status_log | 不写入 |
| 核销方式 | 手动核销 / 批量核销 | 仅直接领用 |
| Redis 缓存 | 更新 device_batches 映射 | 不涉及 |
| 自动审核 | 关联批次用于 L2 规则判断 | 不参与自动审核 |
| 效益分析 | 按项目+设备分摊成本 | 按科室均摊成本 |
| 开瓶稳定期 | 追踪 stability_days_after_open | 不追踪 |

##### 4.5.4.4 业务规则

| 规则编号 | 内容 |
|---------|------|
| **CONS-01** | 领用时校验当前库存：库存 ≤ 最低库存警戒线时弹出预警，不阻断 |
| **CONS-02** | 领用后库存扣减实时生效，三级库可见 |
| **CONS-03** | 领用记录写入 consumption_record（type=CONSUME, device_id=NULL, material_type=CONSUMABLE/OTHER） |
| **CONS-04** | 领用不可逆——提交后无法撤销（区别于试剂核销可修改） |
| **CONS-05** | CONSUMABLE 不需要审批（区别于试剂的设备关联策略），护士即可操作 |

---

### 4.6 M09 进销存日清月结（★ 验收指标放宽）

#### 4.6.1 平衡公式（不变）

```
期初 + 入库 - 出库（消耗+调拨+退供+报损）± 盘盈亏 = 期末
```

#### 4.6.2 ⚠️ 验收指标变更（关键）

| 指标 | 原 PRD | 一期 MVP | 理由 |
|---|---|---|---|
| 日结差异率 | < 0.1% 冻结 | **< 1% 预警，可解释** | 现实消耗核销误差天然 >0.1%，硬指标会验收失败 |
| 平衡验收 | 连续 10 工作日 <0.1% | **连续 10 工作日差异可追溯+有原因说明** | 务实 |

> **这是对原 PRD 最重要的修正**。原 PRD 的"<0.1%平衡"是不可达指标，会埋下验收失败雷。一期改为"可解释的差异"。

---

## 5. ★ 特色一：效益分析模块

### 5.1 核心成本模型

#### 5.1.1 双层成本定义

```
标称成本 C_nominal = 采购单价 / 标称测试数
                    （说明书说一个试剂盒做多少测试）

实际成本 C_actual  = (采购成本 + 隐性损耗) / 有效检测次数
                    （系统算出来的真实成本）
```

#### 5.1.2 实际成本公式（分子=成本，分母=有效产出）

**分子：采购成本 + 四类隐性损耗**

| 成本项 | 公式 | 数据源 | 一期可得性 |
|---|---|---|---|
| 采购成本 | 采购单价 × 数量 | M04 订单 | ✅ |
| 过期浪费 W_exp | 过期报废数 × 单价 | UDI 状态机 EXPIRED | ✅ |
| 开瓶损耗 W_open | 开瓶废弃数 × 单价 | M08 开瓶记录 | ✅ |
| 复测浪费 W_retest | 复测样本数 × 标称成本 | LIS retest_flag | ⚠️ 依赖 LIS |
| 质控消耗 W_qc | 质控次数 × 标称成本 | M11（二期） | ❌ 一期用估算系数 |

**分母：有效检测次数 N_eff**

```
N_eff = 患者样本数（产生收入的部分）

排除：质控测试、校准/定标、复测（只算一次）、报废测试
```

#### 5.1.3 最终公式与衍生指标

```
C_actual = (采购成本 + W_exp + W_open + W_retest + W_qc) / N_eff

浪费率    Waste_Ratio = (C_actual - C_nominal) / C_nominal × 100%
成本偏离度 = C_actual / C_nominal
```

**业务含义**：标称做 100 测试，实际产出 75 个有效结果，浪费率 33%——**这是主任一直看不见的钱**。

### 5.2 试剂收益分析（收入-成本闭环）

#### 5.2.1 项目级毛利

```
单项目毛利 = 项目收费 - 试剂成本 - 耗材成本 - 分摊成本
毛利率 = 毛利 / 项目收费 × 100%
```

#### 5.2.2 DRG 亏损项目识别

```
if 单项目成本 > 单项目收费:
    标记"DRG亏损项目" → 推送主任，建议替换品牌或优化流程
```

### 5.3 一期交付：三张核心报表（最小可用集）

一期效益分析**只做这三张表**，就能打动主任：

#### 报表 1：单测试实际成本表（按试剂 × 批次）

| 试剂 | 批次 | 采购成本 | W_exp | W_open | W_retest | 有效次数 | C_actual | C_nominal | 偏离度 |
|---|---|---|---|---|---|---|---|---|---|
| CK-MB | BATCH-2025A | ¥3,200 | ¥0 | ¥640 | ¥320 | 75 | ¥55.7 | ¥32 | 174% |

#### 报表 2：品牌成本对比表（按项目 × 品牌）

| 项目 | 品牌 | C_actual | 市占率 | 月消耗金额 | 建议 |
|---|---|---|---|---|---|
| 肌酐 | A 牌 | ¥1.8 | 70% | ¥12,600 | 维持 |
| 肌酐 | B 牌 | ¥1.5 | 30% | ¥5,400 | 建议提量 |

#### 报表 3：浪费点 Top10（按金额排序 + 建议）

| 排名 | 试剂 | 浪费类型 | 浪费金额 | 占比 | 建议 |
|---|---|---|---|---|---|
| 1 | CK-MB | 开瓶损耗 | ¥640 | 20% | 换小包装 |
| 2 | ALT | 过期 | ¥1,200 | 35% | 降采购频次 |

### 5.4 浪费点定位算法（规则版，AI 雏形）

```python
for 试剂 in 所有试剂:
    if W_exp占比 > 30%:
        建议 = "采购过量，建议降低单次采购量或提高采购频次"
    elif W_open占比 > 20%:
        建议 = "开瓶损耗高，建议更换小包装规格或合并检测批次"
    elif W_retest占比 > 15%:
        建议 = "复测率高，建议排查设备状态或质控流程"
    elif C_actual/C_nominal > 1.5:
        建议 = "整体浪费严重，建议全面复盘操作规范"
```

### 5.5 看板指标（主任驾驶舱"成本分析"面板一期版）

**第一层：全景指标（每日刷新）**
- 今日试剂消耗总成本
- 单测试成本偏离度
- 检验项目综合毛利率
- 试剂资金占用
- 试剂周转天数（健康 7-14 天）

**第二层：下钻分析**
- 品牌对比 / 批次对比 / 检验组对比 / 项目 Top10 / 时间趋势

**第三层：行动建议**（规则版，见 5.4）

### 5.6 数据来源依赖说明（落地关键）

| 公式项 | 数据源 | 一期可得性 | 兜底方案 |
|---|---|---|---|
| 采购成本、过期、开瓶 | 本系统 | ✅ | — |
| 标称测试数、单价 | M01 目录 | ✅ | 初始化必采 |
| 复测浪费 | LIS | ⚠️ | 一期若 LIS 无字段，**该成本项置 0，报表标注"未含复测"** |
| 质控消耗 | M11（二期） | ❌ | 一期用行业经验系数 5%-8% 估算 |
| 项目收费 | HIS 计费 | ⚠️ | 一期用 HIS 月结数据**批量导入**，不追求实时 |

> **一期策略**：先做"成本侧"（采购+过期+库存），收入侧用 HIS 月结数据批量导入。**报表精度 80% 即可，胜过主任现在的"0 可见度"。**

---

## 6. ★ 特色二：批次感知自动审核模块

### 6.1 核心差异化：三层规则架构

```
┌─────────────────────────────────────────────────────┐
│  Layer 1: 结果规则（传统，LIS 也能做）                │
│    范围检查 / 危急值 / Delta check / 绝对值           │
├─────────────────────────────────────────────────────┤
│  Layer 2: 批次规则（★ 护城河，LIS 做不到）           │
│    该结果用的试剂批次 → 批次健康度检查                 │
│    · 批次效期/开瓶状态                                │
│    · 批次历史异常率                                  │
│    （质控状态一期可选，完整 Westgard 放二期）          │
├─────────────────────────────────────────────────────┤
│  Layer 3: 联动规则（跨域触发，价值最高）              │
│    证照异常 → 拦截相关试剂结果                        │
│    库存冻结 → 拦截相关批次结果                        │
│    （质控失控→拦截 放二期，依赖完整 QC 引擎）         │
└─────────────────────────────────────────────────────┘
```

> **一期重点做 L1 + L2 + 部分 L3**。完整 L3（质控失控联动）依赖二期 Westgard 引擎，一期用"人工标记批次异常 → 触发拦截"过渡。

### 6.2 规则引擎数据结构

#### 6.2.1 统一规则 schema

```json
{
  "rule_id": "R-BATCH-003",
  "name": "批次质控失控拦截",
  "layer": "BATCH",
  "priority": 90,
  "enabled": true,
  "scope": {
    "test_items": ["*"],
    "test_groups": ["IMMUNO", "BIOCHEM"]
  },
  "condition": {
    "type": "BATCH_STATUS",
    "operator": "IN",
    "value": ["OUT_OF_CONTROL", "MANUAL_HOLD"]
  },
  "action": "HOLD_REVIEW",
  "reason_template": "试剂批次 {batch_no} 状态异常({batch_status})，结果需人工复核",
  "link_to": "qc_event_id"
}
```

#### 6.2.2 规则动作枚举

| Action | 含义 | 优先级 |
|---|---|---|
| `AUTO_RELEASE` | 自动放行 | 1（最低） |
| `WARN_RELEASE` | 放行带警告 | 2 |
| `HOLD_REVIEW` | 转人工审核 | 3 |
| `REJECT_RETEST` | 拒绝，需复测 | 4（最高，终止） |

#### 6.2.3 一期内置规则库

| 规则ID | 层 | 触发条件 | 动作 |
|---|---|---|---|
| R-RST-001 | L1 | value 超参考范围 ×1.5 | WARN_RELEASE |
| R-RST-002 | L1 | value 触及危急值 | HOLD_REVIEW |
| R-RST-003 | L1 | 与上次结果差值 >50% | HOLD_REVIEW |
| R-BATCH-001 | L2 | 试剂效期 <7 天 | WARN_RELEASE |
| R-BATCH-002 | L2 | 试剂开瓶超稳定期 | HOLD_REVIEW |
| R-BATCH-003 | L2 | 批次被人工标记异常 | HOLD_REVIEW |
| R-BATCH-004 | L2 | 设备距下次校准 <7 天 | HOLD_REVIEW |
| R-LNK-001 | L3 | 试剂证照过期 | REJECT_RETEST |
| R-LNK-002 | L3 | 批次库存被冻结 | HOLD_REVIEW |

### 6.3 审核决策流水线

#### 6.3.1 单条结果处理流程

```
observation 进入（来自 LIS）
     │
     ▼
① 批次关联：根据 (device_id, test_datetime) 查 Redis 缓存 → 得 batch_no
     │   （缓存由 M08 上机记录维护，关键步骤）
     ▼
② 加载上下文：批次状态、效期、开瓶、证照、患者历史值
     │
     ▼
③ 规则匹配：按 priority 降序，依次执行 L1→L2→L3 规则
     │
     ▼
④ 决策汇总：
     - 任一规则 HOLD/REJECT → 取最高优先级动作
     - 全部 PASS → AUTO_RELEASE
     │
     ▼
⑤ 同步回写 LIS + 记录审计日志
```

#### 6.3.2 决策算法（伪代码）

```python
def audit(observation):
    batch = resolve_batch_via_redis(observation.device_id, observation.test_datetime)
    context = build_context(observation, batch)

    decision = Decision(action=AUTO_RELEASE, priority=1)
    triggered_rules = []

    for rule in enabled_rules.order_by(priority_desc):
        if rule.matches(context):
            triggered_rules.append(rule)
            if rule.action.priority > decision.priority:
                decision = Decision(rule.action, rule.priority)
            if rule.action == REJECT_RETEST:
                break  # 最高优先级，终止

    return AuditResult(decision, triggered_rules, batch)
```

### 6.4 ★ 核心技术命门：批次关联

#### 6.4.1 难点

LIS 推送的 observation 长这样：
```
{患者, 项目, 值, 设备ID, 测试时间}
```
**它不知道用了哪个试剂批次。**

#### 6.4.2 解决方案：时间区间匹配（独家能力）

```
observation (设备=AU5800-01, 时间=09:15)
        +
本系统上机记录 (设备=AU5800-01, 批次=BATCH-2025A, 上机=08:00, 退机=12:00)
        ↓ 时间区间匹配
推导：该结果由 BATCH-2025A 产生
```

**这就是一期必须做 M08 的根本原因——它不只是库存管理，是自动审核的数据基础。**

#### 6.4.3 Redis 缓存设计（性能关键）

```
Key:   ivd:device_batches:{device_id}
Value: Sorted Set，按 load_time 排序
Member: {batch_no}:{load_time}:{unload_time}
```

- 上机时 `ZADD`，退机时 `ZADD` 更新 unload_time
- 审核时 `ZRANGEBYSCORE` 查 test_datetime 落在哪个区间
- **绝不能每次审核查数据库**（性能要求 <500ms）

> **冲突处理**：同一设备同一时间多个批次在机（多试剂通道），按 test_item 反查该批次是否含此项目。一期简化：取最近上机的匹配批次。

### 6.5 LIS 接口契约（详见第 8 章）

一期三个核心接口：
- **接口 A**：LIS → 本系统，结果推送 + 同步返回审核决策（`POST /audit/observations`）
- **接口 B**：LIS → 本系统，人工审核结果回流（`POST /audit/manual-feedback`）
- **接口 C**：本系统 → LIS，批量审核状态查询（`GET /audit/status`）

### 6.6 审核率 KPI

```
自动审核率 = AUTO_RELEASE 数 / 总审核数 × 100%
拦截准确率 = (系统拦截且人工也拒绝) / 系统总拦截数 × 100%
误拦截率  = (系统拦截但人工放行) / 系统总拦截数 × 100%
```

**行业标杆**：自动审核率 60-80%（不是越高越好，太高=规则太松有风险）。

---

## 7. 核心数据模型

### 7.1 一期核心表（11 张，V1.1 新增 1 张）

| 表 | 说明 | 数据规模（年） |
|---|---|---|
| `reagent_catalog` | 物料字典（含 material_type, tests_per_kit, price_per_unit） | 数百 |
| `five_code_mapping` | 五码映射（★ 仅 REAGENT/CALIBRATOR/QC_MATERIAL 类型写入） | 数百 |
| `udi_backlog` | ★ UDI 待补全追踪表（V1.1 新增） | 数十 |
| `supplier` | 供应商 + 证照 | 数十 |
| `reagent_batch` | **批次（核心生命周期实体）** — ★ CONSUMABLE/OTHER 使用虚拟批次号 | 2-7 万 |
| `udi_status_log` | UDI 状态流转日志（不可变，MySQL RANGE 分区按月） — ★ 仅含 REAGENT/CALIBRATOR/QC_MATERIAL | 18-73 万 |
| `warehouse` | 三级库（自引用树） | 数十 |
| `device` | 检验设备注册 | 数十 |
| `device_batch_load` | **★ 上机记录（自动审核命脉）** — ★ CONSUMABLE/OTHER 不写入 | 数万 |
| `consumption_record` | 消耗记录（★ 新增 material_type 字段，含 INBOUND 和 CONSUME 两种类型） | 数十万 |
| `audit_rule` / `audit_decision_log` | 规则定义 + 审核决策日志 | 规则数百 / 日志数十万 |

### 7.2 预留表（二期填充，一期建空表）

- `qc_result`（质控结果）
- `poct_device` / `poct_operator` / `poct_qc_result`
- `ai_consultation_log`

### 7.3 关键索引设计

```sql
-- 批次关联性能命脉
CREATE INDEX idx_device_batch_load ON device_batch_load(device_id, load_time, unload_time);

-- 审核日志查询
CREATE INDEX idx_audit_log_date ON audit_decision_log(decided_at, decision);

-- UDI 追溯
CREATE INDEX idx_udi_log_batch ON udi_status_log(batch_no, occurred_at);
```

### 7.4 UDI 状态机（沿用全本 PRD 第 6 章，V1.1 扩展）

#### 7.4.1 适用范围

UDI 状态机**仅适用于 material_type ∈ {REAGENT, CALIBRATOR, QC_MATERIAL}** 的物料。CONSUMABLE / OTHER 类型不进入状态机，库存增减直接通过 consumption_record 记录。

#### 7.4.2 状态定义

| 状态 | 英文 | 是否终态 | 含义 |
|------|------|---------|------|
| RECEIVED | 已签收 | ❌ | 到货已扫码确认，待正式入库 |
| IN_STOCK | 在库 | ❌ | 已入库上架，可被调拨/领用 |
| **PENDING_UDI** | **★ UDI 待补全（V1.1 新增）** | ❌ | **手工登记入库但 UDI 缺失，批次冻结不可调拨不可上机** |
| TRANSFERRED | 调拨在途 | ❌ | 已从源库房发出，目标库房尚未确认收货 |
| IN_USE | 使用中 | ❌ | 已上机装载到检验设备，正在消耗或待消耗 |
| CONSUMED | 已消耗 | ✅ | 消耗核销完成（不可逆） |
| RETURNED | 已退回 | ❌ | 从设备退回库存（可重新入库） |
| EXPIRED | 已过期 | ✅ | 有效期已过，不可使用 |
| RECALLED | 已召回 | ✅ | 厂家或监管部门要求召回 |
| BACK_SUPPLIER | 已退供应商 | ✅ | 退货完成 |
| SCRAPPED | 已报损 | ❌ | 申报损耗，待销毁 |
| DESTROYED | 已销毁 | ✅ | 销毁确认完成 |

#### 7.4.3 状态转移矩阵（V1.1 扩展）

```
                    ┌─── RECALLED (任意非终态→召回,终态)
                    │
RECEIVED ──inbound──► IN_STOCK ──transfer──► TRANSFERRED
   │        (扫码入库)    │         (调拨)           │
   │                     │                          │
   │                     │ load_on_inst             │ arrive
   │                     │ (上机)                    │ (到达确认)
   │                     ▼                          ▼
   │                  IN_USE ◄─────────────── IN_STOCK
   │                     │
   │          ┌──────────┼──────────┐
   │     consume    return_to    expire_on
   │     (消耗)     _stock(退库)  _inst(在机过期)
   │          │         │           │
   │          ▼         ▼           ▼
   │     CONSUMED   RETURNED     EXPIRED
   │      (终态)        │         (终态)
   │                     │
   │              IN_STOCK (重新入库)
   │
   ├──back_supplier──► BACK_SUPPLIER (终态)
   │
   └──scrapped──► SCRAPPED ──destroy──► DESTROYED (终态)

★ V1.1 新增路径：
RECEIVED ──(udi缺失)──► PENDING_UDI ──(udi补全)──► IN_STOCK
   │                        │
   │                        │ (冻结·不可调拨·不可上机)
   │                        │
   └── back_supplier ───────┘ (也可直接从PENDING_UDI退货)
```

#### 7.4.4 PENDING_UDI 状态说明

**触发条件**：material_type ∈ {REAGENT, CALIBRATOR, QC_MATERIAL} 的物料，验收时 UDI-DI 缺失，库管员通过「📝 手工登记」通道入库。

**冻结限制**：
- ❌ 不可调拨（TRF 操作按钮禁用）
- ❌ 不可上机（M08 扫描入口不显示该批次）
- ❌ 不入设备-批次 Redis 缓存
- ❌ 自动审核流水线跳过该批次
- ✅ 库存可见（可在 M06 三级库中查询）
- ✅ 可退货（BACK_SUPPLIER）

**解冻路径**：
1. 用户在 UDI 待补全面板（§4.1.7）或批次详情页补全 UDI-DI
2. 系统自动校验 UDI 格式 + 五码匹配
3. 校验通过 → 状态自动流转：`PENDING_UDI → IN_STOCK`
4. 恢复正常：可调拨、可上机、入 Redis 缓存

**超时告警**：PENDING_UDI 持续 > 7 天，系统推送告警至库管员 + 科主任："批次 {batch_no} UDI 待补全已超 7 天，请尽快处理"

---

## 8. 系统集成接口契约

### 8.1 一期集成全景

```
HIS ──── 计费数据(月结批量) ──► 效益分析（收入侧）
LIS ──── 结果数据(实时推送) ──► 自动审核（核心集成）
LIS ◄── 审核决策(同步回写) ──── 自动审核
本系统 ── 人工反馈 ◄── LIS（审核结果回流）
```

> **一期不碰仪器直连，全部走 LIS 旁路。** 这是经过风险权衡的决策。

### 8.2 接口 A：LIS → 本系统（结果推送 + 同步审核）

```http
POST /api/v1/audit/observations
Content-Type: application/json
X-Source: LIS
Authorization: Bearer <jwt>
```

**Request:**
```json
{
  "observation_id": "OBS-20260618-001",
  "patient_id_hash": "a1b2c3d4...",
  "test_item_code": "CRE",
  "value": 350.5,
  "unit": "umol/L",
  "reference_range": {"low": 44, "high": 133},
  "critical_range": {"low": 20, "high": 530},
  "device_id": "AU5800-01",
  "test_datetime": "2026-06-18T09:15:00",
  "order_id": "ORD-2026-001",
  "previous_value": 95.2,
  "previous_datetime": "2026-06-15T10:00:00",
  "retest_flag": false
}
```

**Response（同步，LIS 等待决策）:**
```json
{
  "code": 200,
  "data": {
    "observation_id": "OBS-20260618-001",
    "decision": "HOLD_REVIEW",
    "reason": "试剂批次 BATCH-2025A 开瓶超稳定期，结果需人工复核",
    "triggered_rules": ["R-BATCH-002"],
    "batch_no": "BATCH-2025A",
    "decided_at": "2026-06-18T09:15:01",
    "audit_latency_ms": 280
  }
}
```

**性能要求**：P95 < 500ms（LIS 同步等待，超时影响出报告）。

### 8.3 接口 B：人工审核结果回流（闭环训练数据）

```http
POST /api/v1/audit/manual-feedback
```

```json
{
  "observation_id": "OBS-20260618-001",
  "system_decision": "HOLD_REVIEW",
  "manual_decision": "RELEASED",
  "auditor_id": "TECH-005",
  "auditor_note": "复测后确认值正常",
  "recheck_value": 92.1
}
```

**价值**：积累"系统拦截→人工放行"数据，算误拦截率，是规则优化和未来 AI 训练的数据。

### 8.4 接口 C：批量审核状态查询（LIS 对账）

```http
GET /api/v1/audit/status?batch_no=BATCH-2025A&date=2026-06-18
```

返回该批次当天所有结果审核决策，供 LIS 对账和质控追溯。

### 8.5 接口 D：HIS 计费数据导入（月结，批量）

```http
POST /api/v1/finance/charge-import   (Excel/CSV 批量上传)
```

一期不追求实时，每月从 HIS 导出计费数据批量导入，用于效益分析收入侧。

### 8.6 接口鉴权与可靠性

- LIS/HIS → 本系统：JWT + IP 白名单
- 同步接口（A）：超时 3s 返回 `AUTO_RELEASE`（兜底，不能因审核失败卡住出报告）
- 异步接口（B/C/D）：重试 3 次指数退避，失败入死信队列
- 所有接口调用记录审计日志

> **⚠️ 兜底设计**：审核服务不可用时，**默认 AUTO_RELEASE 放行**，避免本系统故障导致医院停摆。这是医疗系统的安全底线。

---

## 9. 非功能需求

### 9.1 性能指标（一期）

| 指标 | 目标 | 说明 |
|---|---|---|
| UDI 扫码响应 | < 200ms (P95) | 验收场景 |
| 库存查询 | < 500ms (P95) | 多维筛选 |
| **审核决策延迟** | **< 500ms (P95)** | **核心，LIS 同步等待** |
| 批次关联查询 | < 50ms | 必须走 Redis |
| 并发扫码 | ≥ 3000 次/小时 × 4h | 比原 PRD 5000 降档（务实） |
| 系统可用性 | ≥ 99.5%（一期） | 三期升 99.9% |

### 9.2 安全需求

| 需求 | 措施 |
|---|---|
| 等保 | 一期过等保二级（三级放三期） |
| 数据加密 | 传输 TLS 1.2+；患者 ID 列加密 |
| 访问控制 | RBAC + 高危操作二次确认 |
| 审计追踪 | 所有操作 5W 不可篡改 |
| PHI 保护 | 患者 ID 入库前哈希脱敏 |

### 9.3 技术栈定稿

> **决策背景**：一期目标客户为三甲/公立医院，信创为硬性准入要求。数据库选 **MySQL 8.0**——它既是主流生产级数据库，又与达梦 DM8 等国产库语法高度兼容，**未来信创切换成本最低**（原 PRD 15.5 节即此思路："数据库层改为 MySQL 8.0，兼容达梦并预留信创切换接口"）。选型遵循"**主流稳定 + 信创可平替 + 一期不引入重设施**"三原则。

#### 9.3.1 一期技术栈清单

| 层 | 组件 | 版本 | 选型理由 |
|---|---|---|---|
| **语言/框架** | Java + Spring Boot 3 | JDK 17 / 3.2.x | 医疗行业主流，信创中间件兼容性好，人才储备足 |
| **构建** | Maven | 3.9.x | 生态成熟、信创环境构建稳定（原 PRD 用 Gradle，此处改 Maven 以降低国产化环境构建风险） |
| **ORM** | MyBatis-Plus | 3.5.x | SQL 可控性强，动态查询灵活（进销存多维筛选/审核规则匹配场景多），对 MySQL 支持成熟 |
| **数据库★** | **MySQL** | **8.0**（InnoDB / utf8mb4） | 主流稳定；与达梦语法兼容，信创切换成本最低 |
| **缓存** | Redis | 7.x | 批次关联性能命脉，<50ms 要求 |
| **消息队列** | **一期不引入 Kafka** | — | 降运维复杂度；用 MySQL 事务 + Spring `ApplicationEvent` + Redis Stream 轻量队列实现状态机事件（二三期再上 Kafka） |
| **对象存储** | MinIO | RELEASE.2024+ | 证照/验证报告 PDF 存储；信创环境可平替为 Ceph RGW |
| **任务调度** | XXL-Job | 2.4.x | 日清月结、FEFO 重算、证照预警定时任务 |
| **API 网关** | Spring Cloud Gateway | 4.x | 一期单体不需要独立网关，预留；**不引入 APISIX**（原 PRD 用 APISIX，一期过度） |
| **前端** | Vue 3 + Element Plus + ECharts + Vite | 3.4 / 2.x / 5.x | 看板生态成熟，ECharts 满足 L-J 图/成本趋势图 |
| **工作流** | **一期不引入 Camunda** | — | 用轻量状态机（Spring StateMachine）实现审批流；Camunda 放三期 |
| **鉴权** | Spring Security + JWT | 6.x | RBAC + IP 白名单；**不引入 APISIX JWT**，由应用层处理 |
| **连接池** | HikariCP | （Spring Boot 内置） | MySQL 下无兼容问题，性能最优 |
| **迁移** | Flyway | 9.x（MySQL 驱动） | 数据库版本管理 |
| **部署** | **Docker Compose** | 单机/小集群 | 一期私有化交付，不上 K8s（原 PRD R4 风险：医院 IT 接不住） |

#### 9.3.2 与全本 PRD 技术栈的差异（重要变更）

| 组件 | 全本 PRD（终局） | 一期 MVP | 变更原因 |
|---|---|---|---|
| 数据库 | PostgreSQL 15 | **MySQL 8.0** | 主流稳定，信创可平替达梦/OceanBase |
| 构建 | Gradle | **Maven** | 国产化环境构建更稳定 |
| ORM | （未明确，隐含 JPA） | **MyBatis-Plus** | 动态查询场景多，SQL 可控 |
| 消息 | Kafka | **无（轻量队列）** | 一期降运维，事件量未到 Kafka 门槛 |
| 网关 | APISIX | **Spring Cloud Gateway（预留）** | 一期单体不需要独立网关 |
| 工作流 | Camunda 8.6 | **Spring StateMachine** | 一期审批流简单，不上重引擎 |
| 部署 | K8s + Helm | **Docker Compose** | 医院 IT 运维能力有限 |
| 读写分离 | Pgpool-II | **一期不做** | 单机够用，二三期再考虑 |

#### 9.3.3 信创切换路径（MySQL → 国产库）

MySQL 8.0 作为一期主选，当目标医院要求数据库国产化时，按以下路径切换，**应用层改动最小**：

| 切换目标 | 兼容度 | 切换成本 | 说明 |
|---|---|---|---|
| **达梦 DM8（MySQL 兼容模式）** | ★★★★ | 低 | 达梦提供 MySQL 兼容模式，DDL/DML 改动小；JDBC 换驱动即可 |
| **OceanBase（MySQL 租户）** | ★★★★★ | 极低 | MySQL 协议兼容，应用几乎零改动 |
| **GaussDB / TiDB** | ★★★★ | 低 | MySQL 语法兼容 |
| **人大金仓 KingbaseES** | ★★★ | 中 | 偏 Oracle 语法，需一定适配 |

> **一期工程约定**：所有 DDL/SQL 严格使用 MySQL 标准方言，**不使用 MySQL 私有扩展函数**（如 `GROUP_CONCAT` 依赖场景做好替代），保证切换时改动量可控。MyBatis-Plus 通过 `DbType` 切换方言。

#### 9.3.4 三期演进路径（技术债务的偿还计划）

一期刻意"轻装"，技术债务在二三期按业务量驱动偿还，不提前优化：

| 偿还项 | 触发条件 | 演进到 |
|---|---|---|
| 上 Kafka | 日事件量 > 10 万 / 多院部署 | Kafka + Exactly-once |
| 拆微服务 | 团队 > 15 人 / 模块独立交付节奏 | Spring Cloud 微服务 |
| 上 K8s | 客户数 > 10 / 需弹性扩缩 | K8s + Helm |
| 上 Camunda | 审批流 > 10 种 / 跨系统流程 | Camunda 8 |
| 上 APISIX | 对外接口 > 20 / 强限流需求 | APISIX 网关 |
| 仪器直连 | 二期接 POCT/质控设备 | instrument-gateway（Go，独立服务） |
| AI 服务 | 三期，数据飞轮成熟 | Python + FastAPI + vLLM + Qdrant（独立部署，与主系统解耦） |
| 数据库信创 | 医院硬性要求 | 切达梦/OceanBase（见 9.3.3） |

### 9.4 信创适配明细

一期采用"**OS + 中间件信创，数据库 MySQL（可平替）**"策略，平衡合规与落地效率：

| 层 | 一期选型 | 信创平替（医院要求时切换） | 适配方式 |
|---|---|---|---|
| CPU | x86 / arm64 | 鲲鹏 920 / 飞腾 S2500 | Docker 多架构镜像（amd64 + arm64） |
| OS | CentOS 7 兜底 | 麒麟 V10 / 统信 UOS | 基础镜像用 Kylin/UOS 官方镜像 |
| 数据库 | **MySQL 8.0** | 达梦 DM8 / OceanBase / GaussDB | 见 9.3.3 切换路径 |
| 应用中间件 | 内嵌 Tomcat（jar 包） | 东方通 TongWeb 7（war 包） | Maven profile 切换打包 |
| 缓存 | Redis 7 | Tendis / Tair 开源版 | Redis 已在信创目录 |
| 对象存储 | MinIO | Ceph RGW | MinIO 已在信创目录 |

> **⚠️ 关键决策点（应用中间件）**：东方通 TongWeb 替代 Tomcat 时，Spring Boot 需打成 **war 包外置部署**。**一期默认用内嵌 Tomcat（jar 包 + Docker）交付**，仅当医院强求应用中间件信创时才切 TongWeb war 模式（通过 Maven profile 切换）。**建议销售前置确认此点**——多数医院只卡 OS+DB 信创，应用层自由。

### 9.5 数据库设计约定（MySQL 8.0）

改用 MySQL 后，原 PG 的部分特性需按 MySQL 方式实现。以下为工程约定，DDL 编写时遵循：

| 特性 | MySQL 实现方式 | 说明 |
|---|---|---|
| 规则 schema / observation 原文 | **JSON 类型**（MySQL 8.0 原生） | 直接存 JSON，支持 `JSON_EXTRACT` 查询，无需 CLOB |
| 大日志表分区（udi_status_log） | **RANGE 分区**（按 `occurred_at`） | MySQL 原生分区，月级分区粒度 |
| 主键生成 | **雪花 ID**（应用层）或 `BIGINT AUTO_INCREMENT` | MySQL 无 Oracle 风格序列，优先雪花 ID（便于未来分库） |
| 事件通知 | Spring `ApplicationEvent` + Redis Pub/Sub | MySQL 无 `LISTEN/NOTIFY`，事件机制走应用层 |
| 布尔类型 | `TINYINT(1)` | MySQL 无原生 BOOLEAN |
| 字符集 | **utf8mb4 / utf8mb4_0900_ai_ci** | 支持完整 Unicode（含试剂名生僻字） |
| 时间类型 | `DATETIME(3)`（毫秒精度） | 审计日志/审核决策需毫秒级 |

**SQL 方言约束**（为信创切换预留）：所有业务 SQL 限用标准方言，禁用 MySQL 私有扩展（如某些窗口函数旧式写法），`GROUP_CONCAT`、`ON DUPLICATE KEY UPDATE` 等需评估替代或在切换时单独适配。

### 9.6 一期部署拓扑（Docker Compose 6 服务编排）

#### 9.6.1 拓扑总图

```
┌──────────────────────────────────────────────────────────────────┐
│              医院内网服务器（麒麟 V10 / CentOS 7）                   │
│                                                                  │
│   docker compose (bridge: ivd-net, subnet 172.20.0.0/16)         │
│                                                                  │
│   ┌──────────┐     ┌──────────────────────────────────────────┐  │
│   │  Nginx   │────►│  ivd-app (Spring Boot 3, Java 17, jar)    │  │
│   │  :80     │     │  Flyway 自动建表 · 审核兜底                │  │
│   │  :443*   │     │  dockerfile: ivd-app/Dockerfile           │  │
│   └────┬─────┘     └────┬──────────┬──────────┬───────────────┘  │
│        │                │          │          │                  │
│        ▼                ▼          ▼          ▼                  │
│   ┌──────────┐  ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│   │  MySQL   │  │  Redis 7 │ │  MinIO   │ │ XXL-Job  │         │
│   │  8.0     │  │  :6379   │ │  :9000   │ │ Admin    │         │
│   │  :3306   │  │  alpine  │ │  :9001   │ │ :8081    │         │
│   │  ivd     │  │  no auth │ │  ivd-bkt │ │ xxl_job  │         │
│   │  xxl_job │  │          │ │          │ │          │         │
│   └──────────┘  └──────────┘ └────┬─────┘ └──────────┘         │
│                                   │                              │
│                            ┌──────┴──────┐                       │
│                            │  minio-init │ (一次性创建 bucket)    │
│                            └─────────────┘                       │
└──────────────────────────────────────────────────────────────────┘
         │                             │
         ▼ HTTPS / 国密预留              ▼ REST + JWT
    检验科工作站浏览器              LIS / HIS 院内系统
    (SPA, Vue 3 构建产物)
```

#### 9.6.2 服务清单与健康检查依赖

| 服务 | 镜像 | 端口 | 健康检查 | 启动依赖 |
|---|---|---|---|---|
| **mysql** | `mysql:8.0` | 3306 | `mysqladmin ping` → healthy | — |
| **redis** | `redis:7-alpine` | 6379 | `redis-cli ping` → healthy | — |
| **minio** | `minio/minio:latest` | 9000, 9001 | `curl /minio/health/live` | — |
| **minio-init** | `minio/mc:latest` | — | 完成即退出 (exit 0) | minio healthy |
| **xxl-job-admin** | `xuxueli/xxl-job-admin:2.4.1` | 8081 | `curl /xxl-job-admin/actuator/health` | mysql healthy |
| **ivd-app** | CI 构建产出 (`ivd-app:${TAG}`) | 8080 | `curl /actuator/health` → healthy (Flyway 成功后) | mysql healthy + redis healthy + minio healthy |
| **nginx** | `nginx:stable-alpine` | 80, 443* | `curl /nginx-health` | ivd-app healthy |

> **启动链路**：mysql / redis / minio 先就绪 → xxl-job-admin + ivd-app 后启动 → nginx 最后就绪。健康检查保证启动顺序，避免应用层连不上 DB 就 crash。

#### 9.6.3 数据卷与持久化

| 数据卷 | 宿主机路径 | 用途 | 备份策略 |
|---|---|---|---|
| `mysql_data` | `./data/mysql` | 业务数据 + binlog | 每日全量 `mysqldump` + binlog 增量 |
| `redis_data` | `./data/redis` | 批次缓存 / 会话 | 冷备（重启后重建缓存，数据不丢） |
| `minio_data` | `./data/minio` | 证照 PDF / 验证报告 | 异地副本 / 跨节点同步 |
| `nginx/html` | `./nginx/html` | 前端构建产物 | CI 构建时更新，Git 保留版本 |

#### 9.6.4 资源建议（二甲~三甲规模）

| 规格 | 最低（开发/演示） | 推荐（二甲） | 充裕（三甲） |
|---|---|---|---|
| CPU | 4 核 | 8 核 | 16 核 |
| 内存 | 8 GB | 16 GB | 32 GB |
| 磁盘 | SSD 100 GB | SSD 500 GB | SSD 1 TB |
| Docker | 20.10+ | 24+ | 24+ |
| Compose | v2 | v2.20+ | v2.20+ |

> 若切换 TongWeb war 部署：额外增加 4C/8G。信创环境（麒麟 V10）基础镜像替换为对应 OS 官方镜像。

> 部署资产清单、PRD 对照验证、生产环境加固建议（自动备份/资源限制/监控）详见 `deploy/README.md`，本文档不再重复。

---

## 10. 实施路线（13 周）

### 10.1 Sprint 规划

| Sprint | 周次 | 交付 | 里程碑 |
|---|---|---|---|
| S0 | W1-2 | 脚手架：Spring Boot 项目、PG/Redis、Docker Compose、CI、Flyway | 环境就绪 |
| S1 | W3-4 | M01 试剂目录 + 五码映射 + 供应商 CRUD | 基础数据 |
| S2 | W5-6 | UDI 引擎：码制识别、状态机、追溯日志 | 地基核心 |
| S3 | W7-8 | M04 采购 + M03 证照 + 扫码验收 | 进货闭环 |
| S4 | W9 | M06 三级库 + FEFO | 库存闭环 |
| S5 | W10 | **M08 上机消耗（★ 自动审核数据基础）** | 数据枢纽 |
| S6 | W11 | **S1 效益分析（三张报表 + 成本模型）** | **特色①** |
| S7 | W12 | **S2 规则引擎 + S3 自动审核 + LIS 接口** | **特色②③** |
| S8 | W13 | M09 日清月结 + 看板 + 联调 UAT | **Go-Live** |

### 10.2 关键路径

```
M01 → UDI引擎 → M04 → M06 → M08(关键!) → S3自动审核
                                          ↘ S1效益分析
```

**M08 是关键路径节点**——它同时是库存管理和自动审核的数据来源，必须按期完成。

### 10.3 外部依赖管理

| 依赖 | 触发时间 | 风险 |
|---|---|---|
| LIS 厂商提供结果推送接口 | S7 前 | **最大风险**，需提前 4 周对接 |
| HIS 提供计费数据导出 | S6 前 | 低，月结批量 |
| 医院提供试剂目录基础数据 | S1 前 | 中，需现场采集 |

> **强烈建议**：W1 即启动 LIS 厂商对接谈判，这是项目能否 Go-Live 的命门。

---

## 11. 验收标准

### 11.1 功能验收

| 功能域 | 验收项 | 标准 |
|---|---|---|
| UDI 追溯 | 码制识别准确率 | ≥ 99.5%（500 次混合码制） |
| UDI 追溯 | 状态流转正确率 | 100%（遍历转移路径） |
| 五码映射 | 覆盖率 | ≥ 90% |
| 三级库 | 库存一致性 | **连续 10 工作日差异可解释**（放宽） |
| FEFO | 出库推荐准确率 | ≥ 99% |
| **效益分析** | **三张报表正确性** | **与手工核算偏差 <5%** |
| **批次关联** | **关联准确率** | **≥ 95%**（时间区间匹配） |
| **自动审核** | **决策延迟** | **P95 < 500ms** |
| 自动审核 | 兜底放行 | 服务不可用时 100% AUTO_RELEASE |
| LIS 接口 | 联调通过 | 主干流程跑通 |

### 11.2 ★ 特色验收（差异化证明）

这三项是一期向客户证明价值的核心：

1. **效益分析报表能定位浪费点**：给定某试剂，系统能输出"浪费类型+金额+建议"
2. **批次感知拦截可演示**：人工标记某批次异常后，该批次结果被自动拦截转人工
3. **自动审核率可量化**：上线 2 周后输出自动审核率/拦截率报表

### 11.3 安全验收

- 等保二级测评通过
- 患者数据脱敏覆盖率 100%
- 审计日志完整性（操作可追溯到人）

---

## 12. 风险识别与应对

| 编号 | 风险 | 概率 | 影响 | 应对 |
|---|---|---|---|---|
| **R1** | **LIS 厂商不配合提供结果接口** | **高** | **高** | **W1 即启动对接；选 LIS 配合度高的标杆医院；预留"技师手动拉取"兜底** |
| R2 | 医院试剂目录基础数据不全（缺 tests_per_kit 等） | 中 | 高 | 提供标准采集模板；现场驻场采集；缺失字段用厂家官网/说明书补 |
| R3 | 批次关联准确率不达标（多试剂通道设备） | 中 | 中 | 多批次在机时按 test_item 反查；一期接受 95% 准确率 |
| R4 | 效益分析数据源缺失（LIS 无 retest_flag） | 中 | 中 | 该成本项置 0 + 报表标注"未含复测"；不阻塞上线 |
| R5 | 审核规则误拦截率高，主任弃用 | 中 | 高 | 上线初期规则保守（多放行少拦截）；2 周观察期调优；提供规则开关 |
| R6 | 医院 IT 运维能力不足 | 中 | 中 | Docker Compose 一键部署；详细运维手册 |
| R7 | 试剂目录 tests_per_kit 与实际偏差大 | 中 | 中 | 报表标注"基于标称值"；鼓励医院做标定测试修正 |

### 12.1 最大风险与保底策略

**R1（LIS 配合）是一期成败的命门。** 保底策略：
1. 选医院时**优先选 LIS 是本院自研或厂商关系好的**
2. 接口谈判时**给 LIS 厂商分润或联合方案**，降低配合阻力
3. 实在不行，退而求其次：自动审核模块只做"批次感知报表"（告诉主任哪些批次有问题），不做实时拦截，等二期再上实时

---

## 附录 A：与全本 PRD 的范围差异对照

| 模块 | 全本 PRD | 一期 MVP | 差异说明 |
|---|---|---|---|
| 进销存（M01-M09） | 完整 | ✅ 保留 | 验收指标放宽（0.1%→1%可解释） |
| 设备兼容（M10） | 完整 | 简化 | 只做设备注册，兼容校验简化 |
| 室内质控（M11） | Westgard 引擎 | ❌ 推二期 | 仅做规则配置 |
| 室间质评（M12） | 完整 | ❌ 推二期 | — |
| 不良事件（M13） | 完整 | ❌ 推二期 | — |
| POCT（M14） | 完整 | ❌ 推二期 | — |
| AI（M15） | DeepSeek 本地化 | ❌ 推三期 | 一期效益分析规则版即雏形 |
| 系统集成（M16） | FHIR 全集成 | **简化为 LIS 旁路** | 不碰仪器直连 |
| 工作流（M17） | Camunda | ❌ 推三期 | 一期用轻量审批 |
| 主任驾驶舱（M18） | 五大看板 | 简化为 2 张 | 成本分析 + 审核统计 |
| **自动核销** | 设备上报吸样量 | **❌ 砍掉** | 设备不上报，落地不了 |
| **★ 效益分析** | 未单列 | **✅ 新增为特色** | 成交器 |
| **★ 自动审核** | 未单列 | **✅ 新增为特色** | 钩子，批次感知 |
| **★ 统一规则引擎** | 散落各模块 | **✅ 提炼为特色** | 大脑 |

---

## 附录 B：ISO 15189 一期合规矩阵

> **基准标准**：CNAS-CL02:2023《医学实验室质量和能力认可准则》（等同采用 ISO 15189:2022）  
> **说明**：本附录仅列一期已覆盖条款；完整合规矩阵见全本 PRD 附录B。

| ISO 15189 条款 | 核心要求 | 一期系统对标 | 模块 | 演示路径 |
|---------------|---------|-------------|------|---------|
| **4.2** 保密信息 | 患者信息保密 | 患者ID入库前SHA-256哈希脱敏；RBAC权限控制 | 系统安全 | 数据库查patient_id_hash字段 |
| **6.6.2.4** 库存监控 | 在效期内使用，监控库存水平 | FEFO先进先出自动推荐出库批次；近效期30/90天分级预警；开瓶稳定期追踪 | M06 三级库 | 三级库→FEFO推荐表→查看推荐顺序 |
| **6.6.2.5** 批号追溯 | 批号可追溯至患者 | UDI码全生命周期追溯（采购→入库→调拨→上机→消耗→患者关联），药监局飞检秒查 | M04-M09 UDI主线 | UDI追溯查询→输入批号→PDF导出 |
| **6.6.3** 性能验证 | 新批号使用前验证 | 开放通道试剂须上传性能验证报告，审核后标记"已验证兼容" | M10 设备兼容 | 设备兼容矩阵→试剂详情→验证报告 |
| **6.4.2-6.4.4** 设备验收/标识/维护 | 设备应有唯一标识并按计划维护 | 设备资产码注册；校准到期<7天预警（R-BATCH-004） | M10 / S2规则 | 规则引擎→R-BATCH-004 |
| **6.8.2** 供应商评价 | 评价并选择供应商 | 供应商电子档案+证照有效期追踪+逾期自动冻结采购 | M03 证照管理 | 证照管理→查看供应商列表→证照状态 |
| **7.4.1-7.4.2** 结果报告/自动选择 | 结果应准确；自动审核规则须经验证 | **批次感知三层规则引擎**（L1结果/L2批次★/L3联动）；九条内置规则独立开关；审核率KPI | S2 规则引擎 / S3 自动审核 | S3自动审核→审核统计→拦截率/误拦截率 |
| **7.4.3** 结果修改 | 修改须保留原始值可追溯 | 审计日志5W（谁/何时/何处/操作/原因）；系统决策+人工决策+修改原因+复核值 | X02 审计日志 / S3 | S3→人工反馈记录→查看审核决策详情 |
| **7.6.3-7.6.4** 数据安全/完整性 | 防非授权访问/数据完整 | 传输TLS+存储AES-256+RABC鉴权；udi_status_log APPEND-ONLY不可变审计追踪 | 系统安全 / X02 | 审计日志→按操作人/时间查询 |
| **7.8** 应急预案 | 服务中断时应急预案 | 审核服务不可用时100% **AUTO_RELEASE**兜底（医疗安全底线，PRD 8.6） | S3 自动审核 | 模拟：关闭审核服务→LIS结果自动放行 |

> **一期覆盖率**：ISO 15189 第4-8章38个关键条款中，一期直接覆盖 **21项（55%）**，主要集中在第6章（试剂/设备/供应商）和第7章（报告/审核/数据安全）。
> 
> **一期评审话术**："我们的系统覆盖了 ISO 15189 中试剂批号追溯(6.6.2.5)、自动审核规则验证(7.4.1.2)、数据完整性(7.6.4)等核心条款——这些都是评审员必查项。二期上线QC引擎和不良事件管理后，覆盖率可到84%。"

---

## 附录 C：销售用 ROI 测算逻辑

### B.1 给主任算账（输入 → 输出）

**输入**（医院规模）：
- 日均检测量、试剂年采购额、检验师人数、当前 LIS 有无审核模块

**输出**（年化价值）：
1. **降本价值** = 试剂年采购额 × 浪费率（行业 15-25%）× 可优化比例（40%）
   - 例：年采购 500 万 × 20% × 40% = **40 万/年**
2. **人力价值** = 检验师人数 × 自动审核率（60%）× 人均工资 × 投入审核时间占比
   - 例：10 人 × 60% × 12 万 × 30% = **21.6 万/年**
3. **合规价值** = 避免一次飞检/不良事件的隐性损失（定性，但主任最在意）

**合计年化价值 ≈ 60 万+**，对一套 30-50 万的实施费，**回收期 < 1 年**。

### B.2 一句话话术

> "一年帮你省 40 万试剂浪费 + 21 万人力，飞检还能秒出追溯报告。系统费半年回本。你的 LIS 算不出单测试实际成本，也拦不住质控失控批次——我能。"

---

> **文档版本记录**
> V1.0-MVP — 2026-06-18 — 一期范围定稿，整合进销存地基 + 三大特色（效益分析/规则引擎/批次感知自动审核）
> V1.1-MVP — 2026-06-29 — 引入物料类型分类、UDI待补全清单、数量验收、耗材消耗、PENDING_UDI状态、自动拆单
> V1.2-MVP — 2026-06-29 — 文档精简：剔除UX导航规范（→效果图HTML）、UI组件树简化为设计要点、部署验证章节移至deploy/README.md、二期功能分离至《IVD智慧实验室管理系统_二期规划_PRD》
