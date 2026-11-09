# 业务全流程审计第2组（2026-11-06）

## 结论与范围

**不能按用户要求判定通过。** 基线V1.1 / 1f1bf81采用“中心库先出库、形成在途、科室签收再入库”，而用户要求“拣配只预占可用量、签收时中心库实存扣减和科室入库同事务”。在途查询不等于独立预占，已有回归还明确断言拣配后实存下降。

本组对固定基线快照独立审查，不把并行进行的第1组修复当作已部署代码。本轮仅修复第1组；本报告的库存核心问题未实施补丁。四个独立审查透镜全部完成。

## 正式库只读证据

- 直接连接正式 ivd 库、日常 ivd 数据库账号；明确设置事务 READ ONLY，仅执行 SELECT。
- 当前迁移版本20261105002，成功。第1组新计划迁移20261106001尚未应用到正式库。
- 申领状态：FULFILLED 4张，SHORTAGE_PENDING 1张；现有未签收发货流水0条、在途数量0。因此不能用当前数据动态验证拣配与签收的瞬间库存变化，也没有为演示而新增单据。
- 正库存：一级库12批次、数量312.00；二级库7批次、101.00；三级库5批次、38.00。数量跨物料单位，不当作同单位业务总量。
- 未找到reservation表或reserved/locked/allocated_qty等持久化预占字段。
- 结算：REQUISITION ACTIVE 4条、83700.00；CONSUMPTION ACTIVE 1条、3200.00。同结算来源重复组0。存量不重复并不能证明所有并发路径正确。
- 正确的IVD运行地址仍需确认：8080返回API_KEY_INVALID而非IVD认证响应；没有向该服务提交IVD凭据、业务数据或执行写入测试。

## 已核对而不误报的行为

- 领用/调拨模式在科室签收计费；验收模式在验收入库计费；消耗模式在实际消耗计费，其他阶段不重复收费。不是每次签收或每次消耗都应新增账单。
- 签收与结算已有组合事务，且重复签收有防重保护；缺少的是特定财务失败位置的完整回滚断言，不能误报为“没有事务”。
- 历史模式未知时拒绝猜测计费是正确政策；需要纠正无条件宣称已计费的提示，不是自动给未知历史补账。
- 同号追溯是物料/批号证据汇总，不自动等同于单件完整轨迹；精确追溯范围另留第3组核验。

## 发现

20项原始发现，3项不构成已确认缺陷而拒绝，剩余归并14项根因：7 high、7 medium，其中2项测试缺口。全部route=defer，因本轮针对第2组授权为审计，不擅自改预占/签收模型或历史账。

### F1 拣配缺独立预占，物理扣库时点不符（high）

- 原始发现：R1/R12/R16；来源：blind-hunter+edge-case-hunter+intent-alignment。
- 证据：[ProcurementServiceImpl.java:467,608](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java#L467)；[ProcurementServiceImpl.java:608](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java#L608)。
- 内容：拣配减少source.stock并可能转TRANSFERRED；签收仅增目标库，属于出库-在途模型，不是用户要求的预占-签收扣库。 拣配时物理stock已减少，缺少独立预占及签收同事务释放预占和正式扣减。 未签收TRANSFER可查询为在途，不能据此把已减少的物理库存称为仅预占。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F2 扫码把缺失批次替换成另一FEFO批次（high）

- 原始发现：R2；来源：blind-hunter。
- 证据：[PickAndShipDialog.vue:264](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-web/src/components/PickAndShipDialog.vue#L264)。
- 内容：traceBatchId不在可选列表时退回第一FEFO批次，仍记录扫码成功，实物与提交批次可能不同。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F3 跨批次扫码数量归到最后批次（high）

- 原始发现：R3；来源：blind-hunter。
- 证据：[PickAndShipDialog.vue:270,279](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-web/src/components/PickAndShipDialog.vue#L270)。
- 内容：单行_batchId会被后一次扫描覆盖，但_pickQty累计不清，A后扫B可能把两件都记到B。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F4 实发唯一码缺去重和发货绑定（high）

- 原始发现：R4；来源：blind-hunter。
- 证据：[PickAndShipDialog.vue:277,323](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-web/src/components/PickAndShipDialog.vue#L277)、[UniqueCodeService.java:168](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-udi/src/main/java/com/zlth/ivd/udi/service/UniqueCodeService.java#L168)。
- 内容：唯一码重复扫描会重复计数，提交不含选中码；签收按数据库排序迁码，不证明迁移的是实发盒码。不能把重复批次条码计数与唯一序列码去重混淆。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F5 签收缺开瓶效期复核（high）

- 原始发现：R5/R13；来源：blind-hunter+edge-case-hunter。
- 证据：[ProcurementServiceImpl.java:543](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java#L543)；[ProcurementServiceImpl.java:543,551](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java#L543)。
- 内容：拣配检查openExpiryDate，签收只查标签效期和状态；等待期间开瓶超期仍可能入库并按签收模式计费。 标签有效、开瓶稳定期已过的来源在签收时没有相同日期校验。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F6 申领与批次单位缺快照和匹配（high）

- 原始发现：R6；来源：blind-hunter。
- 证据：[ProcurementServiceImpl.java:280,593](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java#L280)。
- 内容：明细展示从当前目录填unit，未保存请求时单位；发货只核对试剂而不核对源批次单位，目录变更可能盒与瓶按同数字互认。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F7 受限在途库存缺异常恢复流程（high）

- 原始发现：R10；来源：blind-hunter。
- 证据：[ProcurementServiceImpl.java:394,545](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java#L394)。
- 内容：旧模型已扣中心库存，召回或过期使签收拒绝，单据只允许草稿作废；未找到分配释放或受控退回中央库状态链，不应移除安全拒绝来绕过。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F8 申领编号存在碰撞（medium）

- 原始发现：R7；来源：blind-hunter。
- 证据：[ProcurementServiceImpl.java:192](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java#L192)。
- 内容：REQ后缀取毫秒%1000，正常并发或同偏移会重复；若唯一索引存在将导致请求失败，不表述为成功保存重复编号。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F9 部分履约状态不能代表尚有待签收流水（medium）

- 原始发现：R8；来源：blind-hunter。
- 证据：[ProcurementServiceImpl.java:576](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-procurement/src/main/java/com/zlth/ivd/procurement/service/impl/ProcurementServiceImpl.java#L576)、[DepartmentReceiveView.vue:74](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-web/src/views/procurement/DepartmentReceiveView.vue#L74)。
- 内容：部分履约且本次所有流水已收后保留PARTIAL_FULFILLED，页面仍显示签收，后端拒绝没有待签收流水。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F10 签收状态统计混用页内与全量（medium）

- 原始发现：R9；来源：blind-hunter。
- 证据：[DepartmentReceiveView.vue:155](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-web/src/views/procurement/DepartmentReceiveView.vue#L155)。
- 内容：fetchData按res.records更新statsByStatus覆盖fetchStats的全量结果，翻页改变全局统计。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F11 签收计费提示与实际模式不符（medium）

- 原始发现：R11；来源：blind-hunter。
- 证据：[SettlementServiceImpl.java:119](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java#L119)、[DepartmentReceiveView.vue:196](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-web/src/views/procurement/DepartmentReceiveView.vue#L196)。
- 内容：未知模式不猜测补账是既定正确规则，但界面无条件声称结算自动生成，未显式说明跳过或待核对。新增历史重结模块不是本项已确认要求。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F12 发货追溯节点显示已完成易混淆签收（medium）

- 原始发现：R20；来源：intent-alignment。
- 证据：[TraceQueryService.java:41](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java#L41)。
- 内容：发货TRANSFER标成调拨至科室且节点统一completed，尽管另有实际签收节点，用户可能误读未签收批次已入科室库。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F13 预占与签收时点测试仍锁定旧口径（medium）

- 原始发现：R14；来源：verification-gap。
- 证据：[StockFlowSafetyIntegrationTest.java:329,407,457](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-server/src/test/java/com/zlth/ivd/server/service/StockFlowSafetyIntegrationTest.java#L329)。
- 内容：HEAD测试明确断言拣货后stock8及失败签收后stock8、两批拣配后总库存18，没有stock保持10/20与预占量断言；实现正确预占模型反会使旧断言失败。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

### F14 科室签收后结算失败回滚未被覆盖（medium）

- 原始发现：R15；来源：verification-gap。
- 证据：[SupplyFlowIntegrationTest.java:311,366](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-server/src/test/java/com/zlth/ivd/server/service/SupplyFlowIntegrationTest.java#L311)、[SettlementServiceImpl.java:67](https://github.com/MuYuanShao/IVD/blob/1f1bf816b839b8308ba0ab176bdf5028cb5054be/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java#L67)。
- 内容：现有事件失败注入在消耗或采购验收，现有科室签收拒绝发生于计费前。没有断言签收之后settlement_event失败时库存/预占/签收标记/码/账单全回滚。
- 分流：defer，待单独授权实施；不把防线拒绝等同于可以删掉防线。

## 拒绝项与边界

- R17：四类结算阶段符合合理需求，并有allFourPoliciesChargeOnlyAtTheirChosenStage回归，不能误报每次签收均须计费。
- R18：用户未明确要求逐项短收。现有分批发货后签收可以成立；异常拒收恢复缺口单列F7，不把操作形式缺少本身当作数据错误。
- R19：批次汇总追溯不自动构成单件查询错误，待第3组明确范围；实际发货码绑定问题单列F4。

## 后续实施建议（未执行）

先设计持久化预占与可用量规则，再同事务实现签收释放预占、中心库扣实存、科室加库存、唯一码迁移和按模式结算。必须明确旧在途已扣库数据与新预占数据的区分，不能上线后再次扣旧单。相应更新旧的提前扣库断言，并增加签收计费失败的全链路回滚用例。完成后才谈迁移和正式业务写入验证。

没有修改正式数据、实现第2组补丁、迁移、部署、提交或推送。
