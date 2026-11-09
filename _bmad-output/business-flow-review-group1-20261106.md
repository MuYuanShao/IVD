# 业务全流程审计第1组（2026-11-06）

## 结论

**第1组不能判定业务闭环通过。** 当前提供目录/证照管理、即将到期查询和直接创建采购订单；没有独立的「中心库采购计划→审批→转订单」资源及 API。后两组尚未审计，不以本组替代预占/签收扣库、消耗结算与完整追溯验收。

基线 V1.1 / 1f1bf81。用户明确要求正式库测试，因此只对正式库执行了受只读事务保护的 SELECT，没有运行会建单、改证照日期、上传或审批的测试。代码审查采用四个独立透镜，均完成；无另行计划文件，按用户业务描述作为验收口径。

## 正式库与运行环境证据

- 目标为本机 MySQL 的 ivd，日常 ivd 数据库账号；查询中设置事务 READ ONLY。
- Flyway 当前 20261105002，最近迁移成功。
- 有效目录 8 条，均为 APPROVED；供应商 3 家；证照 2 张，均为 ACTIVE 且有附件；90天内即将到期证照 0 张，已到期证照 0 张。没有为了测试提醒而改正式证照日期。
- 供应商—试剂关系 10 条；采购计划候选表 0 张；现有采购订单：缺货来源 SENT 1 张、智能补货 PARTIAL_RECEIVED 1 张、人工采购 COMPLETED 5 张。未收齐的采购明细 2 条。
- 正库存且不可用、但仍落在现有补货统计条件内的批次 7 条；这是库存范围缺陷的实际数据证据，未改变其状态。
- 8080 的实际响应为 HTTP401、code=API_KEY_INVALID、message=API访问密钥无效；与 IVD 的认证响应不符，不能把它作为 IVD 接口测试结果。本轮未向该服务提交 IVD 账号、令牌或业务数据。
- 当前 Windows 进程清单未识别到 IVD 后端；没有停止或替换其他项目进程。接口端到端、上传/下载真实 MinIO、过期提醒触发均未判通过。

## SPD-MCP-UI 接入面核对

| 能力 | 当前代码 | 验收状态 |
|---|---|---|
| 新增目录 | POST /api/v1/catalog；目录审批动作已存在 | 代码存在，未在正式库新增测试目录 |
| 证照录入/上传/阅览 | POST /api/v1/license；POST /{id}/upload；GET /{id}/file/{objectKey} | 通用 REST 已存在，真实存储本轮未执行上传验证 |
| 即将到期查询 | GET /api/v1/license/expiry-alerts?days=90 | 有拉取 API；不覆盖已经过期记录，也不等于主动提醒投递 |
| 中心库采购计划 | 未找到独立模型、表、路由 | 未实现 |
| 计划审批并转订单 | 未找到计划提交、批准、驳回与批准转单 API | 未实现；目录审批/科室申领审批不能替代 |
| 直接建采购订单 | /orders/manual、/orders/from-shortage、/orders/from-replenish | 已存在，但不是先计划审批后建单的流程 |

通用 REST 面不等于 SPD 连接器已经交付；本轮也不把「尚无 SPD 专用适配器」本身误判为缺陷，因为用户说的是后续接入。

## 发现与分流

23 项原始发现，按根因合并 19 项：8项 high、11项 medium；其中2项是测试缺口，不是已证明的运行时BUG。0项被拒绝。全部为当前版本既有问题/需求差距，route=defer，未自动改代码。以下保留各审查来源与原始ID。

### F1 采购计划及审批转单尚未实现（high）

- 原始发现：R13、R21；来源：edge-case-hunter + intent-alignment。
- 证据：[ProcurementController.java:173](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/controller/ProcurementController.java:173)。
- 内容：三个入口直接创建PO；未见独立计划、提交、审核、批准转单API，不能让SPD-MCP-UI调用所需流程。 用户明确计划先审批再转单；当前仅目录审批和科室申领审批，这两者不能当作中心库计划审批。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F2 草稿订单可直接进入验收（high）

- 原始发现：R12；来源：edge-case-hunter。
- 证据：[ProcurementServiceImpl.java:1205](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1205)。
- 内容：receiveOrder仅拒绝CANCELLED/COMPLETED，正常新增DRAFT单即可进入验收和结算，无采购计划审批前置。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F3 累计历史消耗被当作补货目标（high）

- 原始发现：R1；来源：blind-hunter。
- 证据：[ProcurementServiceImpl.java:1578](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1578)。
- 内容：目标库存取累计 totalConsumed 与10的较大值，选定分析周期只改变显示日均量，运行越久建议采购量越大。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F4 GET重算无业务角色限制且删除其他待处理建议（high）

- 原始发现：R2；来源：blind-hunter。
- 证据：[ProcurementController.java:266](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/controller/ProcurementController.java:266)、[ProcurementServiceImpl.java:1563](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1563)。
- 内容：普通认证用户可调用无角色限制GET，触发删除本机构全部PENDING建议并重建，使其他采购员已选ID失效。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F5 补货把不可用库存计为存量（high）

- 原始发现：R14；来源：edge-case-hunter。
- 证据：[ProcurementServiceImpl.java:1542](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1542)。
- 内容：stockMap仅排除PENDING_UDI，将有正数的召回、过期、已转移等库存纳入；正式库7条符合该误纳范围。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F6 补货未扣除已有在采数量（high）

- 原始发现：R15；来源：edge-case-hunter。
- 证据：[ProcurementServiceImpl.java:1576](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1576)。
- 内容：建议只用目标减当前库存，生成PO未收货时重算仍建议原数量，已有建议GENERATED防重不能跨新建议识别同一需求。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F7 新增证照可以保存不合法供应商引用（high）

- 原始发现：R8；来源：blind-hunter。
- 证据：[LicenseServiceImpl.java:172](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:172)。
- 内容：create直接保存request.supplierId；该证照行自身的机构注入不能保证关联的supplier属于同机构，新增前未查合法引用。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F8 续证审批依据未作不可变保留（high）

- 原始发现：R9、R18；来源：blind-hunter + edge-case-hunter。
- 证据：[LicenseServiceImpl.java:214](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:214)、[LicenseServiceImpl.java:475](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:475)、[LicenseServiceImpl.java:521](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:521)、[LicenseServiceImpl.java:453](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:453)。
- 内容：RENEW历史保存attachmentKey，后续删除或替换仍物理清理对象，历史依据失效且无受控归档入口。 续证通过后的旧证明key仍可删除替换，提交后清理对象使历史审批无可读证明。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F9 过期证照从预警列表消失（medium）

- 原始发现：R17、R22；来源：edge-case-hunter + intent-alignment。
- 证据：[SupplierLicenseMapper.java:19](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/mapper/SupplierLicenseMapper.java:19)。
- 内容：expiry-alerts仅ACTIVE/EXPIRING且expire_date>=today，过期后从提醒消失，仍能从普通列表另查。 提供的是即将到期拉取查询而非已过期证照列表或主动投递；没有SPD适配器不单独作为缺陷，未来接入并不要求本轮已有连接器。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F10 效期检查没有可确认的任务执行入口（medium）

- 原始发现：R23；来源：intent-alignment。
- 证据：[LicenseService.java:53](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/LicenseService.java:53)、[LicenseServiceImpl.java:437](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:437)、[XxlJobConfig.java:16](D:/IVD/ivd-app/ivd-server/src/main/java/com/zlth/ivd/server/config/XxlJobConfig.java:16)。
- 内容：全库main搜索processExpiryCheck仅声明与实现，licenseExpireCheck只在注释；未见对应Scheduled/XxlJob调用，不能证明自动状态检查已调度。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F11 人工采购的日期输入被丢弃（medium）

- 原始发现：R6；来源：blind-hunter。
- 证据：[CreateManualOrderRequest.java:18](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/dto/CreateManualOrderRequest.java:18)、[ProcurementServiceImpl.java:1132](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1132)。
- 内容：请求与界面接受orderDate和expectedDeliveryDate，保存始终使用当天且不写预计到货日，输入被静默丢弃。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F12 人工采购未按验收方式拆分（medium）

- 原始发现：R4；来源：blind-hunter。
- 证据：[ProcurementServiceImpl.java:1128](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1128)。
- 内容：只按supplierId分组，混合试剂与数量耗材合为一单，未维护acceptanceMethod，与PRD拆单口径不一致。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F13 补货按建议逐条建单而非按供应商分组（medium）

- 原始发现：R5；来源：blind-hunter。
- 证据：[ProcurementServiceImpl.java:1669](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1669)。
- 内容：同供应商同验收方式多建议逐条新建单行PO，不按需求所述供应商批次合并。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F14 采购和缺货报表导出未接下载（medium）

- 原始发现：R7；来源：blind-hunter。
- 证据：[PurchaseList.vue:255](D:/IVD/ivd-web/src/views/procurement/PurchaseList.vue:255)、[ShortageList.vue:162](D:/IVD/ivd-web/src/views/procurement/ShortageList.vue:162)。
- 内容：两个handleExport没有请求或下载，只显示即将下载，无法获取报表。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F15 即将到期状态阈值不符需求（medium）

- 原始发现：R10；来源：blind-hunter。
- 证据：[LicenseServiceImpl.java:703](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:703)。
- 内容：calcStatus把90天以内全部标EXPIRING，PRD区分90天关注与30天即将到期，列表/KPI误归类。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F16 证照详情不展示已有试剂关联（medium）

- 原始发现：R11；来源：blind-hunter。
- 证据：[LicenseServiceImpl.java:159](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:159)。
- 内容：详情relatedReagents固定emptyList；正式库存在10条供应商试剂关系，未用于详情。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F17 补货周期参数缺业务校验（medium）

- 原始发现：R3、R16；来源：blind-hunter + edge-case-hunter。
- 证据：[ProcurementController.java:267](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/controller/ProcurementController.java:267)、[ProcurementServiceImpl.java:1551](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1551)、[ProcurementServiceImpl.java:1550](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1550)。
- 内容：任意int可传入分析日期及除数；负数、零等不是有效业务周期。仅确认缺少范围及语义校验，不把正常过去数据下的零除当作已复现。 days未限制为正数及业务窗口；不采用未经重现的除零/溢出结论。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F18 补货成功转换和重试缺持久化断言（medium）

- 原始发现：R19；来源：verification-gap。
- 证据：[StockFlowSafetyIntegrationTest.java:124](D:/IVD/ivd-app/ivd-server/src/test/java/com/zlth/ivd/server/service/StockFlowSafetyIntegrationTest.java:124)、[ProcurementServiceImpl.java:1723](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1723)。
- 内容：已读ProcurementSafetyTest和StockFlowSafetyIntegrationTest；补货调用均拒绝，成功恢复采购只测manual。没有断言补货成功后GENERATED且同ID重试不新增PO。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

### F19 续证存储证据失败缺持久化断言（medium）

- 原始发现：R20；来源：verification-gap。
- 证据：[StockFlowSafetyIntegrationTest.java:147](D:/IVD/ivd-app/ivd-server/src/test/java/com/zlth/ivd/server/service/StockFlowSafetyIntegrationTest.java:147)、[LicenseServiceImpl.java:204](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:204)。
- 内容：已读renewal成功、角色拒绝与幂等测试，未让已列附件的statObject失败后断言状态不变、无RENEW日志且采购仍拒绝。
- 分流：defer。本轮为审计，不实施补丁；不得在正式库用改业务状态的方式补做演示。

## 下一步边界

- 先确认采购计划的独立状态机、审批角色，以及提醒使用拉取还是推送，再实施新增 API；本报告没有修改或决定这些规则。
- 进行接口实测前，需确认 IVD 运行地址与部署版本，避免向同端口的其他应用发送凭据。
- 如后续在正式库进行目录新增、证照上传或审批测试，必须事先明确测试记录与审计保留策略；不能为清理测试而删除不可变审计记录。
- 第2组重点将按用户新要求验证「拣配只预占，签收时中心库正式扣减、科室增加」，不能用此前“拣货即扣库存”的回归结果宣称满足该要求。

本轮没有代码修复、Flyway 迁移、正式业务写入、提交或推送。

## 后续修复状态（2026-11-06）

上述结论保留为1f1bf81基线审计。后续用户已授权修复第1组，19项已完成本地代码/测试处理，完整后端418通过、6条件跳过，前端46通过，构建成功；未上线，不将正式库说成已具备这些新能力。交付及兼容边界见[修复记录](D:/IVD/docs/business-flow-group1-fixes-20261106.md)。第2组预占模型仍待实施。
