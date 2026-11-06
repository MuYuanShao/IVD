# 进销存代码审计待处理项

## Deferred from: code review (2026-11-03) — 目录新增与证照上传

审计版本：`bda58a2`。范围：目录、证照前后端与测试84个文件、约9219行。三组独立审查均已完成；变更意图审查因无单一原始变更计划跳过。本次为静态审查，未运行测试或构建，未向正式库发送业务写请求。

本页“待处理/defer”表示既有代码问题留待用户授权修复，不代表低优先级或已经解决。业务代码、数据库和运行服务未修改。无独立计划文件，本页为审计待办，不是开发计划。

原始候选25项；排除2项，去重后18项：15项已确认代码缺陷（9高、6中），2项测试缺口，1项尚未证明可利用的安全风险。

### 高风险代码缺陷

- **F1｜附件下载缺少资源归属和附件成员校验。** 来源R7/R25（blind-hunter + edge-case-hunter）。`LicenseServiceImpl.java:642` 查不到当前机构下的证照仍继续按输入ID/key请求MinIO；key不在当前附件列表中也不拒绝。知道另一机构有效ID/key的已登录用户可越权读取；删除失败的旧附件地址也可继续访问。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:642)。route=defer，既有问题待修复。
- **F2｜删除/替换附件先删除文件，数据库回滚无法恢复。** 来源R5/R22（blind-hunter + edge-case-hunter）。旧对象删除成功后，元数据、变更日志或提交失败时，DB回滚恢复旧key但文件已丢失。应在提交后回收旧对象并补偿失败的新对象。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:457)，删除路径另见373行。route=defer。
- **F3｜并发附件操作覆盖整个JSON，丢失更新。** 来源R4/R21（blind-hunter + edge-case-hunter）。两次上传读相同旧列表，最后提交覆盖另一次成功上传；删除与上传并发还可能恢复指向已删文件的元数据。无行锁、版本条件。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:302)。route=defer。
- **F4｜目录删除没有库存、未完成采购或删除审批约束。** 来源R3（blind-hunter）。只检查待审批状态，随后软删目录并硬删五码映射。存在库存或未完成采购时也能执行，后续Service逻辑删除查询失去目录，违反PRD 4.1.3.4。[代码](D:/IVD/ivd-app/ivd-catalog/src/main/java/com/zlth/ivd/catalog/service/impl/ReagentCatalogServiceImpl.java:263)。route=defer。
- **F5｜效期检查漏过已到期证照。** 来源R10/R17（blind-hunter + edge-case-hunter）。查询限定到期日不得早于今天；错过到期当天后，后续检查再也选不到该证，存储状态及统计仍可能为有效/即将到期。列表不动态纠正状态。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/mapper/SupplierLicenseMapper.java:20)。route=defer。
- **F6｜普通编辑可无意解冻证照。** 来源R18（edge-case-hunter）。前端编辑总带有效期，后端不保留FROZEN而重算为ACTIVE/EXPIRING，单改发证机关也可解除冻结，无显式解冻审批。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:198)。route=defer。
- **F7｜证照业务变更记录可伪造操作人。** 来源R20（edge-case-hunter）。新增/更新直接保存客户端operatorId/operatorName；@Valid只要求ID非空，未用认证上下文替换。独立操作审计虽有真实身份，但展示的业务历史仍可被错误归因。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/controller/LicenseController.java:59)，落库见LicenseServiceImpl:611。route=defer。
- **F8｜目录导出静默截断到500条。** 来源R15（edge-case-hunter）。导出仅查第一页10000条，Service实际限制500且没有后续分页。超过500条的目录导出看似成功但不完整。[导出](D:/IVD/ivd-app/ivd-catalog/src/main/java/com/zlth/ivd/catalog/controller/ReagentCatalogController.java:211)、[限制](D:/IVD/ivd-app/ivd-catalog/src/main/java/com/zlth/ivd/catalog/service/impl/ReagentCatalogServiceImpl.java:67)。route=defer。
- **F9｜编辑证照的供应商变更被静默丢弃。** 来源R19（edge-case-hunter）。页面供应商可修改并提交supplierId，但update根本不读取/写入此字段，返回成功仍保留旧关联，影响按供应商查询证照与目录关系。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:178)。route=defer。

### 中风险代码缺陷

- **F10｜目录导入无法新增有效记录。** 来源R14（edge-case-hunter）。组装DTO未设置物料类型，create立即解引用null，所有有名称的新行被catch后标记跳过。[代码](D:/IVD/ivd-app/ivd-catalog/src/main/java/com/zlth/ivd/catalog/controller/ReagentCatalogController.java:244)。route=defer。
- **F11｜证照导入缺供应商、发证日期和有效期，导出中文类型也无法回导。** 来源R16（edge-case-hunter）。中文label传valueOf抛异常；改为枚举code仍因日期为空失败，importBatch仅捕获BizException，实际异常不在其中。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/controller/LicenseController.java:172)。route=defer。
- **F12｜证照关键字搜索前后端均未实现。** 来源R9/R23（blind-hunter + edge-case-hunter）。输入触发刷新，但前端未传keyword，后端亦无过滤条件。[前端](D:/IVD/ivd-web/src/views/license/LicenseList.vue:458)、[后端](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:110)。route=defer。
- **F13｜新增附件不写证照业务变更记录。** 来源R6（blind-hunter）。上传只写元数据、不调用insertChangeLog，详情和“信息变更”均看不到该文件新增事件；@AuditLog是另一审计流。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:311)。route=defer。
- **F14｜物料类型变为耗材后保留旧五码映射。** 来源R2（blind-hunter）。审批只处理需要五码映射的新类型，未清理不再需要映射的情况；详情仍无条件返回旧映射。[代码](D:/IVD/ivd-app/ivd-catalog/src/main/java/com/zlth/ivd/catalog/service/impl/ReagentCatalogServiceImpl.java:344)。route=defer。
- **F15｜可保存发证日晚于到期日的无效日期区间。** 来源R24（edge-case-hunter）。DTO只校验非空，create/update无日期先后校验，矛盾的合规资料可被持久化。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:161)。route=defer。

### 测试缺口（不是已证明的运行时BUG）

- **F16｜审批前后五码值一致性无断言。** 来源R11（verification-gap），中风险缺口。现有测试主要改价格/名称或只检查映射存在，删除映射同步代码仍可能通过；应验证未审批时旧值不动、审批后目录与映射编码一致。[现有测试](D:/IVD/ivd-app/ivd-catalog/src/test/java/com/zlth/ivd/catalog/controller/CatalogChangeRegressionTest.java:61)。route=defer。
- **F17｜有效ID但内容不同的审批快照无拒绝测试。** 来源R12（verification-gap），中风险缺口。现有损坏快照为`{}`，仅ID比较已能拒绝，未证明字段比较及失败事务状态。[现有测试](D:/IVD/ivd-app/ivd-catalog/src/test/java/com/zlth/ivd/catalog/controller/CatalogChangeRegressionTest.java:129)。route=defer。

### 待验证安全风险

- **F18｜活跃文件类型上传与inline响应存在，但尚不能确认存储型XSS。** 来源R8（blind-hunter），maybe-false，潜在高风险。上传无服务器端类型白名单，下载允许HTML/SVG MIME；但当前GET需要Bearer，前端文本转义、PDF专用iframe及SVG图片渲染不足以证明脚本执行。需补真实浏览器可执行的认证访问路径，再判断可利用性，不将其作为已确认XSS。[代码](D:/IVD/ivd-app/ivd-license/src/main/java/com/zlth/ivd/license/service/impl/LicenseServiceImpl.java:655)。route=defer。

### 排除项

- **R1｜编辑已审批目录变为DRAFT导致采购阻断：排除。** PRD 4.1.3.3明确规定编辑草稿/提交分别设置DRAFT/PENDING，现有回归亦断言DRAFT。这是现有产品规则，不能直接按BUG修改。
- **R13｜将GTIN/医保编码清空为null后目录与映射分叉：排除该具体论断。** 当前字符串编辑忽略null，能明确清空null的仅指定数字字段，因此“已有非空编码经该路径审批为null”的触发状态不成立；空字符串则会同步。

后续审计组：采购验收 → 库存申领配送 → 消耗结算 → 权限、报表与追溯。尚未完成这些组的全面审计，不能据本组结论宣称全链路已安全。

## Deferred from: code review (2026-11-03) — 采购验收

审计版本仍为`bda58a2`。本组覆盖38个文件、约5058行，含采购前后端、直接参与入库的库存服务和现有供销链测试。三个审查组均完成，无失败组；无独立变更计划，跳过变更意图审查。静态检查，不运行构建/测试，不向正式业务库发送测试写请求。

规范化原始候选30项（其中一项复合发现拆为“来源存在性”和“重复转换”两项），逐项核实后按根因合并为20项：17项代码缺陷（13高、4中）及3项测试缺口（中）。本组没有需要排除的原始候选。全部记为待处理/defer：既有实现审计，不代表已获自动修复授权。

### 高风险代码缺陷

- **采购F1｜供应商与证照准入未统一执行。** 来源R3/R16/R23，三组交叉确认。手动采购仅判断供应商在当前查询结果中，未判断`isActive`或证照过期/冻结；缺货转采购、直收及补货入口更缺少供应商有效归属校验，可能引用其他机构/不存在的供应商。请求角色鉴权、租户查询过滤都不等于验证引用对象的准入资格，PRD要求的LIC-02采购冻结未落实。[手动入口](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1072)、[直接验收](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1319)。
- **采购F2｜同一缺货单可重复生成采购订单。** 来源R1/R18/R20，三组交叉确认。转换前未锁缺货单、未限定待采购且尚未关联PO；每次复制全部缺货量，最后覆盖`so.poId`。重复点击、网络重试或并发调用可产生多张独立可验收PO，扩大采购义务。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:985)。
- **采购F3｜不存在或不可访问的缺货单仍可生成空采购单。** 来源R2/R17/R22，三组交叉确认。先插PO，后查来源；查不到缺货或明细不会抛错，仍提交空头记录。不声称因此读取到外机构数据，而是产生没有合法来源的本机构PO。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1003)。
- **采购F4｜直接验收绕过目录审批准入。** 来源R4/R24。常规手动采购检查APPROVED，直收只检查目录存在，验收公共路径同样不查审批。可通过API将DRAFT/REJECTED目录生成采购单、在库批次、入库流水及所选模式结算，前端下拉仅显示已批准目录不能替代后端校验。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1314)。
- **采购F5｜UDI缺失仍成为可用库存。** 来源R5。请求与目录无有效DI时，验收只校验批号/日期，库存服务仍设IN_STOCK，不进入PRD 7.4.4要求的PENDING_UDI隔离。空DI可来自历史/直收目录，或目录编辑为空字符串后审批（该编辑校验未检查UDI）。[入库源](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1231)、[库存状态](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/ProcurementInventoryServiceImpl.java:103)。
- **采购F6｜来货UDI可与采购试剂身份不一致。** 来源R6。非null请求DI直接覆盖目录DI，未做五码匹配；可为试剂A的采购行提交试剂B的DI，形成相互矛盾的批次/盒码身份。扫码前端查找也无法保护直接API调用。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1231)。
- **采购F7｜部分验收的同一请求重放会重复入库。** 来源R7。订购10、首次收2后重发同一请求，只要仍未超过10就再次成功，收货变4，新增入库流水、库存/盒码，验收结算模式还会新增账。订单锁与超收检查不能识别同一实物收货；结算按流水去重也无法识别两个不同的新流水ID。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1208)。
- **采购F8｜全部验收后缺货单不闭环。** 来源R8/R25。转换设缺货单PURCHASING，收齐仅更新PO状态/数量，全实现没有将缺货单置COMPLETED的分支，缺货统计及待办持续错误。与下述来源字段未持久化是两个独立根因，单修其中一个不能修好另一个。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1291)。
- **采购F9｜人工直收静默忽略实际供应商与各行库房。** 来源R12/R29。提交固定`supplierId:1`并仅使用首行仓库；后来添加的其他仓库行也全部进入首行库房。两个目标均为合法中心库时会静默错库；若目标不合法则由已有后端校验拒绝，不将该情形说成静默成功。供应商不是1时来源被记错，在其他机构还可能形成无效归属引用。[代码](D:/IVD/ivd-web/src/views/procurement/ReceivingView.vue:666)。
- **采购F10｜缺货量按目标科室库存而非中心库存计算。** 来源R19。`createRequisition`已将`toWarehouseId`解析并校验为科室/组库，生成缺货时却拿它当中心库查库存。中心100/科室0可能误报缺货；中心0/科室存量足够又可能漏掉中心实际采购需求。默认常量1也不具备机构中心库解析语义。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:816)。
- **采购F11｜采购单来源追溯字段未持久化。** 来源R21。缺货转PO仅`setSourceId(shortageId)`，该属性标为`@TableField(exist=false)`，真正持久化的`shortageOrderId`和`reqId`没有赋值。重新查询PO即丢失来源链；缺货表的反向poId不能替代PO详情所读取的正向字段。[赋值](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:993)、[实体](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/entity/PurchaseOrder.java:25)。
- **采购F12｜智能补货可写入零数或负数采购。** 来源R26。`List<Map>`请求直接解析`suggestedQty`，没有手动采购的正数校验，表结构亦无正数CHECK，采购数量、行金额和头金额可为零/负。常规验收仍会拒绝非正收货量，故不把该问题错误描述为负数验收增库。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1621)。
- **采购F13｜补货建议转换缺少幂等及来源约束。** 来源R27。生成PO前未读取/锁定建议、核对PENDING/GENERATED和匹配试剂；仅在插单后把传入ID批量标记GENERATED。旧建议ID可再次提交生成额外采购单，不同于缺货单转换，是另一个需要单独保护的入口。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1605)。

### 中风险代码缺陷

- **采购F14｜采购单号取毫秒取模，存在冲突。** 来源R9/R30。日期加`currentTimeMillis()%10000`每10秒重复一次后缀，并发多供应商请求也会重叠。机构+单号唯一索引会拒绝整笔事务，表现为可见的间歇性采购/直收失败，而非成功保存重复单号。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:986)。
- **采购F15｜非批号耗材被强制要求批号与有效期。** 来源R10。DTO与校验方法对所有类型一视同仁；PRD数量验收规定可不填厂商批号/效期、系统产生虚拟批号，当前合法的无效期耗材必须伪造资料才能入库。[校验](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1698)、[请求](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/dto/ReceiveItemRequest.java:33)。
- **采购F16｜人工验收解析出的UDI未传到后端。** 来源R11。扫码保存`line.udi`，添加验收行及两个提交分支都未携带；批次可能回退目录DI，但盒码生成仍传的是空请求DI，扫描证据丢失。此项与后端UDI内容匹配缺失是不同层次的问题。[代码](D:/IVD/ivd-web/src/views/procurement/ReceivingView.vue:651)。
- **采购F17｜选择采购单后新增/导入明细无法验收。** 来源R28。新增/导入行`itemId=null`，未映射到订单真实明细，订单模式仍提交null，后端@NotNull必然拒绝。原本从PO加载的行不属于此问题。[代码](D:/IVD/ivd-web/src/views/procurement/ReceivingView.vue:650)。

### 测试缺口（不是已证明的运行时BUG）

- **采购F18｜手动采购缺少未批准目录的拒绝测试。** 来源R13（verification-gap）。现有集成helper始终提供APPROVED目录与合法供应商，删除审批条件仍可能全绿。应断言拒绝且订单头/明细数量不变。[入口校验](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1063)、[现有测试](D:/IVD/ivd-app/ivd-server/src/test/java/com/zlth/ivd/server/service/SupplyFlowIntegrationTest.java:62)。
- **采购F19｜直收多行中途失败的整笔回滚未覆盖。** 来源R14（verification-gap）。未找到receiveDirect测试；普通receiveOrder回滚测试不经过直收合成头/明细及逐行委托。应在第二行失败后断言所有订单、库存、流水及结算回滚。代码已有外层事务，不声称已发现事务失效。[直收](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1306)。
- **采购F20｜验收测试未启用唯一码管控。** 来源R15（verification-gap）。fixture全部enable_unique_code=0，缺少每整单位生成一条盒码以及批次、试剂归属、状态的断言，盒码生成分支可被跳过而现有流水/库存测试仍通过。[生成分支](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:1259)。

### 核实后未误报的边界

- 常规验收已有正数量校验、超收检查、采购头/明细锁与事务；不能笼统声称没有并发保护。
- 不将DRAFT可收货单独报BUG：现有采购页把DRAFT作为“待收货”，需要另行产品状态设计决定，而不是仅凭枚举中文“草稿”认定绕过。
- 来源流水级结算防重仍存在；采购F7是相同实物验收重试生成了不同流水，需在收货命令边界解决。
- 原始候选均经核实，本组未确认运行时事务回滚失效；3项测试缺口与17项代码缺陷分开统计。

下一审计组：库存申领配送；之后为消耗结算、权限/报表/追溯。未修复本页事项，未修改业务代码、数据库或服务，也未提交推送。

## Deferred from: code review (2026-11-03) — 库存申领配送

版本`bda58a2`；范围84个文件、约8544行，覆盖申领、拣货、发货、签收、通用调拨及直接参与的库存/流水/结算服务和测试。本组为静态检查，未执行测试、构建或正式业务写请求。

**覆盖限制：blind-hunter审查组因模型容量不足失败；edge-case-hunter、verification-gap两组完成，主审另行核对关键调用、权限和数量路径。不能宣称三组全部通过或已穷尽本组所有BUG。** 16项原始候选核实后排除1项，保留15项：12项代码缺陷（8高、4中）及3项测试缺口。全部为既有实现待处理/defer，未获授权自动修复。

### 高风险代码缺陷

- **配送F1｜申领明细接口缺少父单科室授权。** 来源R9（主审）。`GET /procurement/requisitions/{reqId}/items`直接按reqId查明细，不查父单。租户拦截器只对明细加机构条件，未按父单科室限制，因此科室A技师知道同机构科室B的申领ID即可读取其试剂、数量和价格，即使B的申领主单详情对A不可见。不声称跨机构读取。[服务](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:248)、[接口](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/controller/ProcurementController.java:55)。
- **配送F2｜通用调拨不限制数量小数位，独立舍入可增库。** 来源R11（主审）。`/inventory/transfer`仅要求qty>0，源/目标库存列却为DECIMAL(10,2)。按MySQL十进制舍入，源10.00调0.005时9.995可存为10.00、目标0.005存为0.01，总量变10.01。没有DTO/@Digits/服务层精度拒绝，派生剩余库存表同样会跟随已舍入值。此为代码和DDL推导，未在正式库做增库实验。[入口](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InventoryServiceImpl.java:346)、[库存字段](D:/IVD/ivd-app/ivd-server/src/main/resources/db/migration/V20260701004__init_inventory_warehouse.sql:53)。
- **配送F3｜在途期间召回/过期的来源签收时未重新校验。** 来源R1（edge-case-hunter）。签收只核对源试剂和库房，未核对当前状态/效期，目标库存新建为IN_STOCK；已召回来源可能被复制成新的正常库存。日期在途过期同样会被标为在库，但下游消耗仍有日期检查，不把其说成已证明的过期消耗绕过。[签收](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:522)、[目标状态](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/ProcurementInventoryServiceImpl.java:95)。
- **配送F4｜不同科室并发签收可重复分配同一批唯一码。** 来源R2（edge-case-hunter）。两张申领单、不同目标库只各锁自己的单据/目标库，均可无锁读取同一源批次的IN_STOCK码；无版本/旧归属条件的更新使后提交者覆盖前者，两个科室库存都入库却有一个失去对应盒码。[调用](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:539)、[码分配](D:/IVD/ivd-app/ivd-udi/src/main/java/com/zlth/ivd/udi/service/UniqueCodeService.java:157)。
- **配送F5｜唯一码不足或小数签收仍可全量入库。** 来源R3（edge-case-hunter）。拣选未按“启用唯一码”要求整数，签收用`intValue()`截断，且忽略实际迁码返回值；例如管控物料申领并发0.5，签收库存0.5但迁码0。即使整数量但码不足，也会成功签收和形成结算证据。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:535)。
- **配送F6｜旧签收接口绕过自动结算。** 来源R10（主审）。`PUT /procurement/requisitions/{id}/receive`直接调用采购签收服务，没有走当前前端使用的`POST /settlement/receive`组合事务。库管可通过旧入口完成领用/调拨模式签收却不生成应有账；签收标记只是插表，不发布自动计费事件，正常组合接口此后又会拒绝已完成签收，需人工补救。非指前端当前正常按钮必然漏账。[旧入口](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/controller/ProcurementController.java:110)、[组合事务](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java:65)。
- **配送F7｜审核/作废未与其他状态变更共享串行化约束。** 来源R12（主审）。审核、作废先普通读状态，再按ID全实体更新，没有行锁、版本或旧状态条件。两个审核者读到待审核后，A通过并完成发货，B的延迟驳回仍可把SHIPPED覆盖为DRAFT，留下已扣库/在途流水却不可签收的主单。作废与提交亦可能覆盖后来的状态。[审核](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:345)、[作废](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:388)。
- **配送F8｜通用调拨遗漏来源效期校验。** 来源R5（edge-case-hunter）。只检查IN_STOCK与库存足够，不检查效期；日期已过但状态未更新的批次可移入新目标批次并继续标在库，绕过申领拣选已有的日期校验。下游消耗端日期校验仍存在，不声称其也被绕过。[代码](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InventoryServiceImpl.java:386)。

### 中风险代码缺陷

- **配送F9｜申领汇总把小数数量逐行截成整数。** 来源R13（主审）。后端允许两位小数，列表和详情却逐行`intValue()`后相加，0.5显示0、两行0.75显示总量0而非1.50；VO也用Integer。底层原始数量和账单不因此自动截断，错误在业务展示汇总。[代码](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java:154)。
- **配送F10｜科室签收表格字段与后端契约不符。** 来源R14（主审）。页面绑定`fromDeptName`、`fulfilledQty`、`itemCount`，接口实际返回`deptName`、`shippedQty`、`items`，中间无转换，所以科室/已发货量/品种数列空白。[页面](D:/IVD/ivd-web/src/views/procurement/DepartmentReceiveView.vue:56)、[VO](D:/IVD/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/vo/PurchaseRequisitionVO.java:24)。
- **配送F11｜FEFO优先推荐已过期批次。** 来源R15（主审）。查询仅过滤IN_STOCK和正库存、按效期升序，无“效期不早于今天”限制；发货弹窗默认选第一条。之后真正拣选又拒绝过期批次，用户有正常库存也会先遭遇失败。[推荐](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InventoryServiceImpl.java:149)、[默认选择](D:/IVD/ivd-web/src/components/PickAndShipDialog.vue:213)。
- **配送F12｜多品种部分发货被弹窗阻断。** 来源R16（主审）。表单对每条尚有需求的SKU都强制批次和正数量，没有跳过缺货项的方法。A有货、B缺货时不能只发A；将B置0也被拒绝。后端本可接收部分明细，此为前端阻断；不否认同一SKU少发一部分数量可行。[代码](D:/IVD/ivd-web/src/components/PickAndShipDialog.vue:319)。

### 测试缺口（不是已证明的运行时BUG）

- **配送F13｜缺少真实唯一码迁移结果断言。** 来源R6（verification-gap）。当前部分发货签收测试mock码服务或不创建码，仅断言库存/状态/账单；遗漏迁码、迁走全部码亦可能全绿。需验证在途与签收数量对应码的批次及状态。[现有测试](D:/IVD/ivd-app/ivd-procurement/src/test/java/com/zlth/ivd/procurement/service/impl/ProcurementSafetyTest.java:44)。
- **配送F14｜通用库存调拨的事务回滚未被集成测试覆盖。** 来源R7（verification-gap）。现有真实事务测试没有引入InventoryServiceImpl；需在调拨流水写入失败时断言源/目标库存、状态日志和盒码一并回滚。现有同步MANDATORY事件和事务存在，本项不代表已证实回滚失效。[路径](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InventoryServiceImpl.java:346)。
- **配送F15｜同一申领单并发拣货剩余量约束未测试。** 来源R8（verification-gap）。当前并发测试针对消耗和采购收货，未测两请求用不同源批次争同一申领需求的串行化，应断言只扣一次需求量且流水一致。申领锁当前确实存在，不能误报为未加锁。[现有测试](D:/IVD/ivd-app/ivd-server/src/test/java/com/zlth/ivd/server/service/SupplyFlowIntegrationTest.java:223)。

### 排除项与覆盖边界

- **R4｜选择非最早效期批次必须审批，否则即BUG：排除。** 当前PRD明确FEFO为“出库推荐”，没有无条件强制先到期出库及例外审批约束。可人工选择其他有效批次不能单独认定为缺陷；配送F11是推荐无效过期库存的不同问题。
- 重复整单签收已有主单锁、签收标记及拒绝逻辑，本组未将其误报为无防重；与跨单并发争同一源盒码是不同问题。
- 主审新增条目均标明来源，未将主审检查冒充失败的独立审查组；所有数值与并发结果为代码路径分析，未在正式库执行破坏性验证。

下一组：消耗结算；最后为权限、报表与追溯。所有审计发现尚未修复，未提交推送。

## Deferred from: code review (2026-11-03) — 消耗结算及直接关联日清月结

版本`bda58a2`；45个文件、约3220行。blind-hunter和verification-gap完成，主审逐项核实；**edge-case-hunter因BMad引用的`review-prompts/references/deletion-check.md`缺失而停止**，不得视为三组全部通过。无独立变更计划；静态审查，未运行构建/测试、未发送正式业务写请求。

17项原始候选按根因保留17项：12项已确认代码缺陷（9高、3中）、4项测试缺口、1项价格口径待确认风险。没有重复计数上一组的旧签收入口漏账、调拨舍入和跨单迁码问题。全部为既有代码待处理/defer，未执行修复。

### 高风险代码缺陷

- **消耗F1｜消耗命令重试可重复扣库和计费。** 来源R1（blind-hunter）。同一消耗请求在响应丢失后重发，会生成不同消耗流水ID，再扣库存并按消耗模式生成另一账单。库存锁防超卖、来源索引防同一流水重复计费，但不识别同一实物消耗的重传；例如余量10时重复提交2，两次均可成功。[代码](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/service/impl/ConsumptionServiceImpl.java:103)。
- **消耗F2｜首次上机没有启动开瓶稳定期时钟。** 来源R4（blind-hunter）。只写上机记录并转IN_USE，未初始化open_date/open_expiry_date，新批次两个字段可一直为空。消耗只拒绝非null的过期开瓶日期，审核上下文又将open_date为空当作无限开瓶稳定期，短于标签效期的开瓶限制不生效。[上机](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InstrumentUsageServiceImpl.java:43)、[消耗校验](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/ConsumptionInventoryServiceImpl.java:47)、[审核口径](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/AuditInventoryContextServiceImpl.java:39)。
- **消耗F3｜技师可按已知ID卸载其他科室的上机记录。** 来源R5（blind-hunter）。卸载的load表只有机构过滤；设备/批次查询虽按科室过滤，返回空结果却未阻断，仍更新目标load.unload_time。可提前结束别科室的上机区间，影响后续批次关联与追溯，不声称跨机构修改。[代码](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InstrumentUsageServiceImpl.java:54)。
- **消耗F4｜当日日结后仍可发生业务，快照不能刷新。** 来源R6（blind-hunter）。日结仅拒绝未来日期，允许当天执行；之后同日消耗/入库仍可提交，而再次日结因已有快照被拒。翌日期初优先取前日已存期末，导致早结后的业务永久漏入当日快照，并可能造成翌日伪差异。临时锁库房并不是持久关账约束。[日结](D:/IVD/ivd-app/ivd-finance/src/main/java/com/zlth/ivd/finance/service/impl/FinanceServiceImpl.java:45)、[前日承接](D:/IVD/ivd-app/ivd-finance/src/main/java/com/zlth/ivd/finance/mapper/DailyInventorySnapshotMapper.java:52)。
- **消耗F5｜月结只打印汇总日志，却返回“月结完成”。** 来源R7（blind-hunter）。无月结结果/完成状态落库，未核对日结覆盖或未解释差异，空月份和未来月份也可走成功路径。问题是暴露的月结操作没有实际持久化成果，并非要求擅自恢复PRD已放宽的严格差异冻结。[代码](D:/IVD/ivd-app/ivd-finance/src/main/java/com/zlth/ivd/finance/service/impl/FinanceServiceImpl.java:87)。
- **消耗F6｜期间期初、期末按每天累加，余额失真。** 来源R8（blind-hunter）。getBalanceAggregation对所有日快照SUM(opening/closing)，两天库存均100会显示期间期初200、期末200。应累计的是期间流量，余额应按每个试剂/库房取期间边界；月结和/finance/balance均使用该聚合。[代码](D:/IVD/ivd-app/ivd-finance/src/main/java/com/zlth/ivd/finance/mapper/DailyInventorySnapshotMapper.java:85)。
- **消耗F7｜消耗结算按操作人科室归属，而非实际库存科室。** 来源R9（blind-hunter）。自动/手动消耗结算传入当前scope.departmentId。管理员被允许消耗科室库存，但其自身科室可能为空或不同，导致账记到错误/空科室，未使用消耗流水已有warehouse证据。[自动结算](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java:241)、[手动结算](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java:165)。
- **消耗F8｜日清页面日期与试剂筛选实际未生效。** 来源R16（verification-gap Other findings）。fetchData只传分页和库房；接口无日期过滤，试剂文本亦未传递。选择某一天时表头显示该日，明细仍是所有日期，可能将其他日期消耗当作当日账核对。[页面](D:/IVD/ivd-web/src/views/finance/FinanceView.vue:143)、[接口服务](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java:321)。
- **消耗F9｜当前页原始消耗金额冒充“总结算金额”。** 来源R17（verification-gap Other findings）。统计数量/金额/试剂数只遍历当前页，记录数却采用全量total，翻页就改变“总额”；冲销、未结算或其他模式已付的原始消耗额仍被当作结算额相加，null又被转0。页内消耗总额并不等于有效结算总额。[代码](D:/IVD/ivd-web/src/views/finance/FinanceView.vue:150)。

### 中风险代码缺陷

- **消耗F10｜耗材/其他类沿用了强制设备绑定。** 来源R2（blind-hunter）。新建默认FORCE，物料类型切换未自动豁免；消耗不先区分material_type，缺设备即拒绝，填设备又走上机及可能的UDI状态机。违反PRD4.5.4“直接领用、device_id=NULL、不上机”。改FLEXIBLE可绕开部分阻断，但不应让业务用户如此修补类型规则。[代码](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/service/impl/ConsumptionServiceImpl.java:110)。
- **消耗F11｜柔性关联上机仍强制选择仪器。** 来源R3（blind-hunter）。PRD4.5.3明确上机设备可选、留空为未指定设备，但bind、Controller及页面load模式均强制deviceId。无设备消耗已支持，不等于无设备上机流程已实现。[代码](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InstrumentUsageServiceImpl.java:29)。
- **消耗F12｜选日期后月结在前端抛异常。** 来源R15（verification-gap Other findings）。日期组件没有value-format，用户选择后settleDate成为Date，而月结在try外调用.slice()；日结/统计请求也不再按LocalDate的ISO字符串契约发送。[组件](D:/IVD/ivd-web/src/views/finance/FinanceView.vue:14)、[月结动作](D:/IVD/ivd-web/src/views/finance/FinanceView.vue:180)。

### 测试缺口（不计作运行时BUG）

- **消耗F13｜普通测试不会执行历史金额重建SQL。** 来源R11（verification-gap）。真实SQL测试依赖IVD_RECON_TEST_URL，未见普通测试配置启用；服务测试mock余额。需让跨日发货/签收、每仓期初入出期末金额在实际SQL上被自动验证。[测试](D:/IVD/ivd-app/ivd-finance/src/test/java/com/zlth/ivd/finance/mapper/HistoricalBalanceSqlTest.java:12)。
- **消耗F14｜日结第二条快照失败的整笔回滚未验证。** 来源R12（verification-gap）。FinanceSettlementTest直接new服务、mock Mapper，无事务代理和真实第二行失败；应断言没有部分快照残留且修正后可重试。当前@Transactional存在，不声称其已失效。[测试](D:/IVD/ivd-app/ivd-finance/src/test/java/com/zlth/ivd/finance/service/impl/FinanceSettlementTest.java:38)。
- **消耗F15｜唯一码不足时的消耗回滚分支未覆盖。** 来源R13（verification-gap）。现有整数/计费失败测试不验证“库存足够、码不足”时已写库存/状态/流水/部分码是否全部回滚；源码实际有数量不符即抛错的防线。[分支](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/service/impl/ConsumptionServiceImpl.java:142)。
- **消耗F16｜按批号消耗的多来源冲突未测试。** 来源R14（verification-gap）。混合来源测试使用明确batchId，不经过batchConsume；需验证同批号同设备库房两来源时拒绝、任何库存与账都不变。当前冲突保护确实存在。[分支](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/service/impl/ConsumptionServiceImpl.java:194)。

### 待确认口径，不计为已确认BUG

- **消耗F17｜仅有avg_cost的历史库存如何生成消耗金额。** 来源R10（blind-hunter），maybe-false。库存日结允许用avg_cost估值，消耗只取unit_price；unit_price缺失时，消耗模式会拒绝计费，其他/未知模式可能留下金额为空而阻断日结的流水。需要明确“历史库存估值成本”是否可以作为“消耗计费单价”，或应先补齐权威价格再允许操作。现有禁止猜测补历史账政策下，不能直接把avg_cost当作收费单价自动修复。[消耗取价](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/service/impl/ConsumptionServiceImpl.java:130)。

### 核实边界

- 正数及两位小数校验、批次锁、效期/已有开瓶日期检查、来源模式校验、流水级结算唯一性、冲销行锁和原因留痕均已存在，不重复误报为“完全没有校验或防重”。
- 冲销不回补实物库存符合当前业务边界；仅凭冲销后同一来源不能再次计费，也不足以在未明确重结政策前报BUG。
- 收货旧接口绕过自动结算已归配送F6，本组不重复计数。
- 本次失败的是BMad审查依赖，不是已证明的项目运行异常；所有代码发现为静态调用路径核实，非正式库压测结论。

下一组：权限、报表与追溯。至此仍未修复业务代码、未修改数据库、未部署或提交推送。

## Deferred from: code review (2026-11-03) — 权限、报表与追溯

版本`bda58a2`；本组快照221个文件、约10756行，并跟进快照外TraceQueryService及实际消费者。blind-hunter、verification-gap完成，主审核实；**edge-case-hunter仍因BMad缺少`review-prompts/references/deletion-check.md`中止**，其停止前的三项具体候选均经主审独立核对，不能据此宣称该组完整完成。

19项原始候选，按根因合并为18组：**14项新增代码缺陷（9高、5中）、3项新增测试缺口、1项已计入前组的同源问题新增暴露路径**。不重复全局计数。全部为既有代码待处理/defer，没有自动修复、测试执行、正式业务写入、部署或提交。

### 新增高风险代码缺陷

- **权限F1｜唯一码查询未验证父批次科室归属。** 来源R2（blind-hunter）。已知同机构其他科室的盒码时，先从仅按机构过滤的udi_unique_code取码并组装返回，再查批次；批次被科室隔离隐藏时也不拒绝，仍暴露码状态、批次ID、批号和时间。与跨机构越权不同，是同机构跨科室读取。[代码](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InventoryServiceImpl.java:590)。
- **权限F2｜非临床角色可读取旧患者结果追溯接口。** 来源R3（blind-hunter）。`/inventory/trace/patients/{batchNo}`没有临床角色/功能开关校验，库管、财务只要登录即可读取本机构该批号旧关联表中的患者哈希、项目、结果和检测时间。部门谓词只施加于临床角色，因此非临床角色反而可读全机构；关闭新版临床功能并未关闭此旧接口。不声称其能读取新加密lab_patient档案。[接口](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/controller/InventoryController.java:92)、[查询](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InventoryServiceImpl.java:629)。
- **权限F3｜旧患者关联仅按批号字符串混合不同试剂。** 来源R9（blind-hunter）。主追溯已区分reagent_id+batch_no，但患者二次请求只带batchNo，旧关联表亦无试剂/批次身份。两种试剂复用同一厂商批号时，会把有权看见但属于另一试剂的患者结果混在本次追溯中，不依赖越权才能触发。[后端](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/InventoryServiceImpl.java:629)、[前端](D:/IVD/ivd-web/src/views/trace/TraceView.vue:184)。
- **权限F4｜新自动审核记录缺少科室归属，写后不可见。** 来源R11（edge-case-hunter部分结果，经主审核实）。迁移已加department_id，实体无该字段、流水线不赋值，公共填充只处理时间/机构。技师新审核记录保存NULL科室，随后的decisions/status/feedback及统计又按科室过滤，因此自己也查不到；人工反馈查不到后还静默返回成功。历史未知科室不推定不适用于此处有明确上下文的新记录。[写入](D:/IVD/ivd-app/ivd-audit/src/main/java/com/zlth/ivd/audit/service/impl/AuditPipelineServiceImpl.java:109)、[实体](D:/IVD/ivd-app/ivd-audit/src/main/java/com/zlth/ivd/audit/entity/AuditDecisionLog.java:12)。
- **权限F5｜审核日志未保存实际参与判定的结果值。** 来源R17（主审）。testValue加入规则上下文用于判定，但组装AuditDecisionLog时未调用setTestValue，Mapper/公共填充也不会补齐。提供数值结果的审核请求仍保存空结果值，后续无法仅依持久化证据还原判定输入。应完善受控证据保存，不是把临床原始信息打印到普通日志。[代码](D:/IVD/ivd-app/ivd-audit/src/main/java/com/zlth/ivd/audit/service/impl/AuditPipelineServiceImpl.java:112)。
- **权限F6｜全生命周期追溯遗漏已有UDI状态事件。** 来源R6（blind-hunter）。时间线未读udi_status_log，已记录的召回、退回、报损等时点和操作人不出现，当前状态可能已召回而生命周期无对应事件。这里只要求展示可证明关联的已有事实，不能为补齐时间线把所有同批号历史跨来源拼接，更不能改写不可变日志。[追溯](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java:24)、[已有日志写入](D:/IVD/ivd-app/ivd-udi/src/main/java/com/zlth/ivd/udi/service/UdiStateMachine.java:69)。
- **权限F7｜效益报表把未定价数据漏算或当零。** 来源R16（verification-gap Other findings）。AVG/SUM跳过NULL，再COALESCE为0，页面直接显示货币，混合已定价/未定价记录显示不完整总成本却无提示，全未定价显示0。与工作台“证据缺失应显示未知”的口径相矛盾，独立于此前日清页面当前页汇总问题。[SQL](D:/IVD/ivd-app/ivd-analytics/src/main/java/com/zlth/ivd/analytics/mapper/EfficiencyMapper.java:18)、[显示](D:/IVD/ivd-web/src/views/analytics/AnalyticsView.vue:88)。
- **权限F8｜品牌消耗成本重复计入入库和调拨。** 来源R18（主审）。getBrandComparison没有record_type=CONSUME筛选，却将所有流水金额SUM为totalCost，前端称“月消耗金额”。一份物料入库100、调拨100、消耗100会统计成300。与NULL缺价是不同根因，补缺价提示不能解决流水类别重复计量。[SQL](D:/IVD/ivd-app/ivd-analytics/src/main/java/com/zlth/ivd/analytics/mapper/EfficiencyMapper.java:29)、[显示](D:/IVD/ivd-web/src/views/analytics/AnalyticsView.vue:135)。
- **权限F9｜工作台漏算继承科室归属的组库。** 来源R19（主审）。合法三级库可只填parent_id、dept_id为空；库存权限及新剩余库存页面会沿父级解析归属，但工作台BATCH_SCOPE/LEDGER_SCOPE仅比较本库dept_id。组库库存、消费金额、效期预警和趋势因此从本科室报表中丢失，造成同科室不同页面对不上账。[代码](D:/IVD/ivd-app/ivd-analytics/src/main/java/com/zlth/ivd/analytics/mapper/WorkbenchMapper.java:12)。

### 新增中风险代码缺陷

- **权限F10｜用户可被成功分配到无效/跨机构科室，随后无法使用API。** 来源R4（blind-hunter）。创建/编辑直接保存deptId，事后查询只补名称，不校验存在、启用及归属；请求链随后因非法归属全面拒绝该用户。需管理员再修资料。[代码](D:/IVD/ivd-app/ivd-system/src/main/java/com/zlth/ivd/system/service/impl/UserServiceImpl.java:87)。
- **权限F11｜验收结算节点从追溯中消失。** 来源R5（blind-hunter）。追溯把非CONSUME流水一律按TRANSFER查账，而实际验收账类型为INBOUND、source_id为入库流水ID，故已存在的验收结算永远查不到。不是认定账未生成。[代码](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java:34)。
- **权限F12｜PDF追溯导出仅有提示，没有文件。** 来源R7（blind-hunter）。点击后只显示“生成中，即将下载”，无请求、生成或下载动作；不与已实现的临床报告PDF混为一谈。[代码](D:/IVD/ivd-web/src/views/trace/TraceView.vue:229)。
- **权限F13｜追溯遗漏已有五码字段。** 来源R8（blind-hunter）。响应未选GTIN、未提供前端读取的nmpaCert，已有registrationCertNo也未映射为该键；目录存在数据，追溯仍显示破折号。[后端](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java:23)、[前端](D:/IVD/ivd-web/src/views/trace/TraceView.vue:199)。
- **权限F14｜管理员合法切换机构后工作台拒绝账号。** 来源R12（edge-case-hunter部分结果，经主审核实）。请求链允许显式选其他机构并留痕，但userScope在所选机构租户过滤下查询仍属本院的管理员ID，结果为空，返回账号不存在/停用。属于身份目录与数据机构混用，不是要扩大admin既有授权。[服务](D:/IVD/ivd-app/ivd-analytics/src/main/java/com/zlth/ivd/analytics/service/impl/WorkbenchServiceImpl.java:25)、[查询](D:/IVD/ivd-app/ivd-analytics/src/main/java/com/zlth/ivd/analytics/mapper/WorkbenchMapper.java:19)。

### 已有问题的新暴露路径，不重复计数

- **权限F15 → 消耗F3同源：追溯也泄漏别科室上机记录。** 来源R1/R10（blind-hunter + edge-case-hunter部分结果）。device_batch_load缺少科室谓词；合法查询中心库共有批号后，追溯扩大查询所有同批号上机记录，LEFT JOIN设备的过滤只把设备名称变NULL，不能移除别科室load行，操作人/时间/ID仍返回。与此前“可跨科室卸载”共用同一上机记录隔离根因，修复应同时覆盖读与写。[代码](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java:55)。

### 新增测试缺口（不计为运行时BUG）

- **权限F16｜刷新接口缺少机构变更后旧刷新令牌的拒绝测试。** 来源R13（verification-gap）。当前helper测试验证token工具本身，未进入AuthServiceImpl.refresh核对账号迁机构后旧token返回40100。现有服务校验确实存在。[分支](D:/IVD/ivd-app/ivd-server/src/main/java/com/zlth/ivd/server/service/impl/AuthServiceImpl.java:77)。
- **权限F17｜自动签收未验证两个机构逐个执行及上下文清理。** 来源R14（verification-gap）。context helper测试不能证明scheduler两机构均完成签收结算；当前定时器已按机构建立SYSTEM_JOB范围，未把其存在本身误报为BUG，正式配置保持关闭亦为既定决定。[调度](D:/IVD/ivd-app/ivd-server/src/main/java/com/zlth/ivd/server/scheduler/AutoReceiveScheduler.java:53)。
- **权限F18｜驾驶舱缺价金额SQL缺少实际数据库断言。** 来源R15（verification-gap）。AnalyticsEvidenceTest mock Mapper，只测其他未接指标/空趋势；WorkbenchMapper的独立测试不能覆盖DashboardMapper。应验证已定价精确和、混合缺价为NULL、空集为0。[SQL](D:/IVD/ivd-app/ivd-analytics/src/main/java/com/zlth/ivd/analytics/mapper/DashboardMapper.java:16)。

### 本组未误报的边界

- admin+SYSTEM_ADMIN固定全功能例外属于用户明确要求，不作为漏洞；机构切换仍应显式选择并留痕。
- 在检查到的业务原生JDBC调用中，未证明使用未覆盖重载而逃逸机构隔离，不因存在JdbcTemplate就宣称泄漏。
- 刷新令牌与访问令牌类型、账号角色/机构变更校验、后台机构范围均已有防线，不重复报告旧版已修复问题。
- 旧患者关联接口问题不能推导成新患者加密档案/PDF已被读取；本轮未做临床模块全部场景或在线渗透验证。

五组进销存主链静态审计至此走完，后续应先统一根因、建立修复/回归清单再实施。所有发现仍待处理；业务代码与正式库未修改，未部署、提交或推送。

## 修复状态更新（2026-11-04，第一批）

以下为审计之后的新状态，前文保留为2026-11-03的历史审查证据，不改写原始结论：

- [x] 目录/证照F1：附件授权、成员校验及no-store响应。
- [x] 配送F1：申领明细查询先验证父单机构/科室可见性。
- [x] 消耗F3、同源权限F15：上机记录自身科室谓词同时约束查询、卸载和追溯扩张。
- [x] 权限F1：唯一码自身行父批次科室约束及无科室时403业务拒绝。
- [x] 权限F2：旧患者结果临床角色、部署开关与服务层双重校验。
- [x] 权限F3：多可见试剂复用同批号时拒绝旧患者关联展示，不猜测补历史身份。
- [x] 权限F4：新临床审核记录使用服务端科室写入已有department_id字段。

7个独立根因本地修复通过；其余63项代码问题及原15项测试缺口、2项待确认风险仍未完成。206项后端回归（200通过、6外部条件跳过、0失败/错误）和22项前端回归通过，前后端构建成功；没有正式部署、数据库修改、提交或推送。

部署前仍需按维护流程核对正式环境、备份和回滚包。未配置临床前提时旧患者接口及前端关联区继续关闭，不能用admin全权限绕过加密/部署前提。详见[本批修复进度](D:/IVD/docs/audit-remediation-progress-20261104.md)。

## 修复状态更新（2026-11-04，第二批）

- [x] 配送F2：调拨数量精度，拒绝独立舍入增库。
- [x] 配送F3：签收时锁定并重新校验来源标签效期/受限状态。
- [x] 配送F4：来源码集合锁定、条件更新，避免并发归属覆盖。
- [x] 配送F5：完整单位和实际迁码数校验，不足整笔回滚。
- [x] 配送F6：保留旧URL，经公共端口统一签收结算。
- [x] 配送F8：通用调拨的来源标签效期校验。
- [x] 采购F2/F3：缺货来源存在性、状态锁及单次转单，禁止空来源PO。
- [x] 采购F8：部分采购保持未完成，完整验收闭环并同事务写领域审计。
- [x] 采购F10：缺货计算的机构中心库来源纠正。
- [x] 采购F11：PO来源关系持久化。
- [x] 配送F13/F14/F15原测试缺口：真实逐码结果、普通调拨失败回滚、并发拣选需求串行化。

本批11项独立代码问题加首批7项，累计18/70；余52项。原测试缺口补齐3/15，余12项；2项候选风险仍待确认。缺货状态与领域审计写入同事务，审计失败回滚用例通过。完整后端246项（240通过、6条件跳过、0失败/错误），含真实MySQL和H2库存安全各20项；前端22项通过。

消耗/部分验收请求级幂等、采购证照准入、智能补货校验和单据审核/作废竞态等仍待后续。临时MySQL库全部清理，正式库未迁移/修改，正式服务未替换，未提交推送。实现和验证边界见[进度文档第二批](D:/IVD/docs/audit-remediation-progress-20261104.md)。

### 第三批状态（2026-11-04）

- [x] 消耗 F1、采购 F7：消耗和验收请求事务回执防重。
- [x] 采购 F19、F20：真实 MySQL 验证直收多行回滚及唯一码生成。

累计修复 20/70 项（余 50 项），补齐测试缺口 5/15 项（余 10 项），2 项待确认风险不变。正式库未迁移，未部署或推送。

### 第四批状态（2026-11-04）

- [x] 采购 F12、F13：补货数量校验、机构内建议锁定、来源试剂匹配及事务状态更新。
- [ ] 采购 F1：补货入口已校验启用供应商，其他入口与证照冻结待完成。
- [ ] 采购 F14：保留既有编号格式，跨批/跨入口并发编号分配仍待修复。

累计修复 22/70 项，原测试缺口补齐 5/15 项，2 项待确认风险不变。补货真实 MySQL 并发回归仍待补齐。
## 统一收尾状态（2026-11-05）

以上为审计时及分批历史快照，不代表当前仍全部待办。原 70 项确认缺陷已完成本地修复，15 项原测试缺口已补齐；两个待确认风险以下载隔离及拒绝未知权威价格的方式保守处置，未宣称复现攻击或确定历史价格。完整后端 355 通过、6 条件跳过、0 失败；前端 43 通过。未部署或改正式库、未提交推送。以 [统一交付说明](D:/IVD/docs/audit-remediation-unified-20261105.md) 和当前代码为准。
