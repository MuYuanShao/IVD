# 采购计划与提醒接入契约（本地实现，待部署）

本契约为后续 SPD-MCP-UI 接入预留标准 REST；未实现远端适配器或推送。必须先迁移 V20261106001 并配套部署后端、前端。正式库目前仍为 20261105002。

## 鉴权及范围

沿用现有 Bearer 访问令牌、Result 包装和机构/科室上下文。机构由服务端确认，普通调用方不能通过正文指定 institutionId；管理员显式切换机构仍走现有审计。不要把本机其他服务的 API_KEY_INVALID 响应当作 IVD 的认证接口。

## 计划资源

基址 /api/v1/procurement/plans：

| 方法与路径 | 作用 | 角色 |
|---|---|---|
| GET / | page/pageSize/status/keyword 分页 | 管理员、采购员、库管、主任、财务 |
| GET /{id} | 明细及生成订单 | 同上 |
| POST / | 保存草稿 | 管理员、采购员、库管 |
| PUT /{id} | 修改草稿或驳回计划 | 创建人或管理员，且具备管理角色 |
| POST /{id}/submit | 提交审批 | 创建人或管理员 |
| POST /{id}/approve | 批准 | 主任或管理员；不得本人审批本人计划 |
| POST /{id}/reject | 驳回，comment 必填 | 同批准 |
| POST /{id}/convert | 批准后按供应商与 SCAN/MANUAL 拆单 | 管理员、采购员、库管 |

状态：DRAFT → PENDING_REVIEW → APPROVED → ORDERED；驳回为 REJECTED，可修改后重新提交。转单重复调用返回已生成的订单，不再新增。状态变更与不可变 plan_event 同事务。

创建/更新头：sourceType、orderDate、expectedDeliveryDate、settlementMode、remark。sourceType 为 MANUAL/SHORTAGE/SMART_REPLENISH。

- MANUAL：items 数组，行含 reagentId、supplierId、quantity。价格来自已批准目录，不接受客户端覆盖。
- SHORTAGE：shortageId、supplierId，明细按缺货来源解析。
- SMART_REPLENISH：suggestions 数组，含 id、reagentId、supplierId（可由目录配送商唯一解析）、suggestedQty。

日期为 YYYY-MM-DD；数量为正数且最多两位小数。批准后目录价格/物料类型变化则拒绝转单，须重新编辑和审批，不静默改价。
返回 PlanVO：id、planNo、status、sourceType、shortageId、头字段、createdBy、reviewedBy、items、orders。ID 不应在客户端经浮点数再转换；按返回值作为标识处理。orders 行含 id、poNo、supplierId、acceptanceMethod、status。

## 兼容与限制

- 旧 POST /orders/manual、/orders/from-shortage、/orders/from-replenish 现在返回采购计划 PlanVO，不再立即返回 PO 数组。这是显式响应契约变化，调用方须同步更新。
- 旧内部工厂创建的新草稿标识 PLAN_REQUIRED，不可直接验收；批准计划转单为 PLAN，订单状态 SENT。
- 新增的 approval_origin 默认区分旧 LEGACY 数据，不给历史订单补造审批；不修改旧原始业务列。
- DIRECT_RECEIPT 为保留的独立直收入口，不将其冒充采购计划审批。若要求所有到货都必须有计划，应另行明确是否关闭这个兼容入口。
- 采购计划不会实现库存预占；第2组要求的拣配预占/签收扣库仍需独立修复。

## 补货与导出

- GET /api/v1/procurement/smart-replenish?days=30：只读取指定周期最新运行的待处理建议，不删除或重算。
- POST /api/v1/procurement/smart-replenish/recalculate?days=30：仅采购管理角色；1–365 天；保留旧批次，空结果也记录运行批次。
- 目标为 max(周期内 CONSUME 日均数量 × 30, 10)，减本机构中心库可用量、有效在采量及待审批/已批准的计划承诺；不按累计历史量越滚越大。
- GET /orders/export 和 /shortages/export：format=xlsx/csv，支持 status/keyword。超过10000条明确拒绝，不返回截断文件。

## 证照提醒与依据

- GET /api/v1/license/expiry-alerts?days=90：包含已到期、冻结和窗口内记录，附 reminderLevel/reminderLabel。
- OVERDUE、URGENT（7天内）、EXPIRING（30天内）、ATTENTION（90天内）、FROZEN、NORMAL、UNKNOWN。
- 已有 EXPIRED/FROZEN 状态不会因普通编辑或日期延长自行解除；续证须走原显式审批。
- LicenseExpiryScheduler 有 Spring 定时入口及 XXL handler licenseExpireCheck，按机构执行 SYSTEM_JOB，上下文清理且错误隔离。默认关闭，部署验证后才按需设置 ivd.license-expiry.enabled=true；必须启用机构隔离。
- 此任务更新效期状态，外部系统可拉取提醒；没有擅自配置 SPD 消息推送。
- 已批准 RENEW 引用的证明附件只读，不允许删除或替换；历史证明信息损坏时保守拒绝破坏性操作。
