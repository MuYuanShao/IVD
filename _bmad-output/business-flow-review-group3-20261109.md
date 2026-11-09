# 第 3 组业务流程审计：科室消耗、结算与追溯

- 审计基线：V1.1，Git `1f1bf81`；不包含工作树中的第 1 组修复。
- 口径：消耗结算在科室消耗，领用／调拨结算在科室签收，验收结算在采购验收入库。同一库存来源只采用一种模式；来源或模式不明的历史库存不自动补账。
- 方法：四个独立 BMad 视角（盲审、边界、测试缺口、意图对齐），复核 HEAD 源码及已有测试断言；正式 MySQL 仅以只读事务核对。36 条原始观察：归并 22 项，拒绝 2 项，2 项不计入本组。21 项登记待修，1 项需确认来源证据口径。
- 实测边界：本地 IVD 后端默认地址的直连健康探测失败，未执行正式 API 写入、浏览器端到端或并发复现；未重跑测试套件。下面区分数据库证据、已有测试和代码可达性。

## 正式库只读核对

Flyway 最新成功版本为 `20261105002`。会话为 `REPEATABLE-READ`，开启 `STRICT_TRANS_TABLES`。

| 核对项 | 结果 |
| --- | --- |
| 消耗流水 | 8 条，数量合计 11，金额 ¥14,750；均缺历史批次 ID 和结算模式快照 |
| 入库／调拨流水 | 入库 5 条（数量 49，¥68,800），调拨 9 条（数量 33，¥98,100）；也均缺这两项快照 |
| 有效结算 | 历史领用 4 条，¥83,700；历史消耗 1 条，¥3,200；合计 ¥86,900，重复完整来源键 0 组 |
| 结算关联 | 1 条消耗结算同机构关联到真实消耗，试剂、批号、数量和金额均一致；4 条领用结算均能关联到申领主单，不能据此证明明细归属 |
| 批次串入 | 4 个当前仍可按试剂与批号查询的「批次＋申领单」组合，含同申领其他试剂或批号的结算；当前查询条件会带入它们 |
| 财务报表科室 | 2 条历史消耗的库房科室与操作人当前科室不同（含空值差异）；不能把操作人科室视作成本归属 |
| 历史证据 | 6 条 UDI 状态日志均缺批次 ID；4 条签收关联均为入库记录恢复证据，不是人工签收时间；结算事件表 0 条 |
| 并发和来源样本 | 唯一码 4 个（3 调拨、1 在库），未制造并发；在库库存中未发现「模式有值但缺采购来源」样本；未发现匹配多个当前库存代次的历史消耗样本 |

以上无法推断其余历史消耗应当补结算。不得通过批号、现价、当前科室或恢复时间回填历史账。

## 风险与复核结论

| ID | 影响 | 证据与建议 |
| --- | --- | --- |
| G3-F1 | 高 | [TraceQueryService.java:70](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java:70) 按申领主单取全部领用结算，正式库有 4 组触发数据。按已匹配调拨流水及来源类型取明细；没有明细证据的旧账单独注明单据级。页面与 PDF 共用该查询。 |
| G3-F2 | 高 | [UniqueCodeService.java:125](D:/IVD/ivd-app/ivd-udi/src/main/java/com/zlth/ivd/udi/service/UniqueCodeService.java:125) 普通 SELECT 取在库唯一码，无旧状态条件更新且未核对行数。两个不同请求均在等库存行锁前建立一致性读快照时，后一个请求可能仍读到前一个已消费的码；两次扣库成功，唯一码数量却少一次。以真实 MySQL 两事务复现后，使用锁定读、状态/批次条件和影响行数校验。 |
| G3-F3 | 高 | [TraceView.vue:198](D:/IVD/ivd-web/src/views/trace/TraceView.vue:198) 患者查询独立异步，旧请求可把 A 批次患者关联显示到 B 批次。以查询代次校验成功、失败和加载状态，不推断跨机构泄露。 |
| G3-F4 | 高 | [SettlementServiceImpl.java:324](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java:324) 结算使用消耗库房归属，明细却按操作人的当前科室展示。优先用已入账科室快照；未结算用库房证据，无法确认则明示。 |
| G3-F5 | 中 | [ConsumptionView.vue:315](D:/IVD/ivd-web/src/views/consumption/ConsumptionView.vue:315) 柔性上机允许设备为空，从上机列表发起消耗却提交字符串 `"null"`，后端 Long 参数解析失败。空值应省略。 |
| G3-F6 | 高 | [ConsumptionServiceImpl.java:166](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/service/impl/ConsumptionServiceImpl.java:166) 合法手工补库存到达流水插入时漏填 `material_type`；正式库非空且无默认，严格模式下整个动作回滚。修复时一并补请求幂等，不能让恢复后的入口重复增库。 |
| G3-F7 | 中 | [TraceQueryService.java:14](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java:14) 用唯一码定位后降为试剂＋批号，同批号不同采购来源和其他唯一码的动作并入。可作为批号级查询，不能称为单件历史；区分查询范围，有单件证据时才展示单件轨迹。 |
| G3-F8 | 中 | [TraceQueryService.java:63](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java:63) 在原入账时间显示当前冲销状态，遗漏实际冲销时间、操作人和原因。应按不可变结算事件分别展示，旧记录不补造。 |
| G3-F9 | 中 | [TraceQueryService.java:41](D:/IVD/ivd-app/ivd-inventory/src/main/java/com/zlth/ivd/inventory/service/impl/TraceQueryService.java:41) 仅凭 `INBOUND` 一律显示采购验收，不能区分手工补录和来源未确认的历史入库。补录入口当前另受 F6 阻塞。 |
| G3-F10 | 高 | [SettlementServiceImpl.java:320](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java:320) 无批次 ID 的明细取同批号同库房最小 ID 的现价。即使当前只剩一个匹配，也不能证明历史来源；保留原流水单价，批次价未知时不推算。未发现正式库现存多匹配。 |
| G3-F11 | 高 | [ConsumptionView.vue:302](D:/IVD/ivd-web/src/views/consumption/ConsumptionView.vue:302) 批次搜索结果受版本保护，但旧弹窗异步完成后仍预填批次与设备；同批号候选仍可选中旧库存。须将预填绑定到本次打开及当前选择。 |
| G3-F12 | 中 | [ConsumptionServiceImpl.java:188](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/service/impl/ConsumptionServiceImpl.java:188) 无设备批量核销先返回，漏批量用途默认，且可把直接领用物料送入 PRD 区分的批量流程。 |
| G3-F13 | 中 | [ConsumptionView.vue:7](D:/IVD/ivd-web/src/views/consumption/ConsumptionView.vue:7) 已有批量核销 API，但没有 PRD 的前端入口。新增入口前先明确库存行、批量粒度和用途。 |
| G3-F14 | 中 | [ConsumptionServiceImpl.java:224](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/service/impl/ConsumptionServiceImpl.java:224) 带设备查询时今日消耗和累计消耗范围不一致。 |
| G3-F15 | 低 | [SettlementServiceImpl.java:208](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java:208) 结算编号格式与规范不同；历史单号不应重写。 |
| G3-F16 | 高 | [ConsumptionController.java:102](D:/IVD/ivd-app/ivd-consumption/src/main/java/com/zlth/ivd/consumption/controller/ConsumptionController.java:102) 与 [SettlementController.java:103](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/controller/SettlementController.java:103) 导出以单页请求 10,000，但全局分页上限 500；第 501 条起静默遗漏。 |
| G3-F17 | 中 | [TracePdfController.java:31](D:/IVD/ivd-app/ivd-server/src/main/java/com/zlth/ivd/server/controller/TracePdfController.java:31) 执行全量查询及逐流水扩展后才检查 PDF 1,000 条上限。查询应有前置范围和行预算。 |
| G3-F18 | 中·回归 | 对手工调拨结算补测试：尚未签收时拒绝，结算记录和结算事件均不增加。 |
| G3-F19 | 中·回归 | 对手工补库存补测试：采购来源同库在库批次仍必须经采购验收，拒绝时库存和流水不变。 |
| G3-F20 | 中·回归 | 对 PDF 下载补测试：成功查询后仅修改输入但不查询，下载仍对应屏幕已展示的批号筛选。 |
| G3-F21 | 高·口径确认 | [SettlementServiceImpl.java:235](D:/IVD/ivd-app/ivd-settlement/src/main/java/com/zlth/ivd/settlement/service/impl/SettlementServiceImpl.java:235) 自动消耗结算匹配模式及库存金额，但不要求采购来源或另一种经审计确认的来源证据。[基线测试](D:/IVD/ivd-app/ivd-server/src/test/java/com/zlth/ivd/server/service/SupplyFlowIntegrationTest.java:93) 明确断言仅有库存行和模式也会入账。正式库未找到这种在库样本。 |
| G3-F22 | 中·回归 | 补同申领多批号、同物理批号不同来源、绑定/免绑定两路消耗→结算→追溯页面及 PDF 的证据匹配测试。 |

## 建议与待确认

先修追溯串批、唯一码并发、患者关联错配、财务明细归属；与第 2 组的预占及签收扣库一起做库存事务回归，避免分别改动同一链路造成口径再分叉。已有四模式、同批号混合模式、同事件重放、结算故障回滚与未知模式不计费测试；本轮检查的是测试内容，没有重跑，也不代表正式链路通过。

G3-F21 有两种实施口径：

1. 自动计费只认采购来源，所有无采购明细的库存都进入人工核对。
2. 允许采购来源或其他经审计确认的入库证据；仅有库存行、单价和结算模式时不自动计费。**推荐 2**，既能覆盖合法的非采购库存，又不会把“模式已知”当作来源已证实。

未计入的问题：拣配时点已登记为 G2-F1；没有直达追溯按钮属于易用性建议，未把它当作业务缺陷。拒绝的推断：目前手工入库先因缺列失败，不能宣称其重复请求已造成正式库重复库存；允许明确证据下的人工结算不等同于自动补历史账。
