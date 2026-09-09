# 首页工作台真实数据契约

## 范围与访问

正式首页 `/dashboard` 读取 `/api/v1/analytics/workbench` 和 `/api/v1/analytics/workbench/products`。前端封装唯一入口为 `src/api/workbench.js`；不再读取历史 `/analytics/dashboard/kpi` 的固定指标。`/workspace/**` 是明确标记的独立 mock 示例，不作为生产首页数据源。

新增 Analytics 只读查询模型，不改变入库、申领、调拨、消耗的写流程，不增加/修改业务表。查询在只读、REPEATABLE_READ 事务中完成；来源过滤值由服务端当前登录用户确定，客户端不能指定查看他人科室。系统管理员（SYSTEM_ADMIN / ADMIN）全院，其他在用账号本科室；无科室账号明确返回 40300，不默认放大全院范围。

## 数据粒度与口径

| 展示             | 来源/口径                                                                                            | 单位与空值                                                       |
| ---------------- | ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| 今日消耗金额     | consumption_record，CONSUME，当天 occurred_at                                                        | 元，BigDecimal；任一消耗流水金额缺失时返回 null                  |
| 今日消耗记录     | 同上，COUNT(\*)                                                                                      | 笔，不是患者数/盒数/支数                                         |
| 今日入库记录     | INBOUND 外部入库流水                                                                                 | 笔；不包含内部调入                                               |
| 今日调拨出库     | TRANSFER 流水，以 from_warehouse_id 为归属库                                                         | 笔；不把调入、调出重复汇总                                       |
| 近30天出入库趋势 | INBOUND 金额与 TRANSFER 金额按 occurred_at 日期分组                                                  | API 元，前端图表及汇总换算为万元；缺失金额保留 null/断点，不补零 |
| 已过期在库       | reagent_batch.stock > 0 且 expiry_date < 今天                                                        | 批次行数                                                         |
| 90天内到期       | stock > 0，今天 ≤ expiry_date ≤ 今天+90天                                                            | 不包含已过期批次                                                 |
| UDI待补全        | stock > 0 且 udi_status = PENDING_UDI                                                                | 批次行数                                                         |
| 未闭环缺货       | shortage_order 的 PENDING_PROCUREMENT / PURCHASING                                                   | 单数，不是缺货商品数                                             |
| 待办             | 未终结的申领单：PENDING_REVIEW / REVIEWED / PICKING / PARTIAL_FULFILLED / SHIPPED / SHORTAGE_PENDING | 总单数与最早创建的6单分开返回                                    |
| 待办进度         | 足量发货的明细项数 ÷ 全部明细项数                                                                    | 不把不同物料单位的数量相加                                       |
| 在库批次列表     | reagent_batch.stock > 0，一行一个批次 ID，关联目录和仓库                                             | 数量保留两位小数及原单位；同试剂不同库/批次不合并                |

日期边界以服务端 Asia/Shanghai 的自然日计算：`[当天00:00, 次日00:00)`；趋势包含今天在内30个自然日。没有流水的日期补真实的零；有流水而金额未维护的日期保留 null。当前没有销售订单/收入来源，因此不能把消耗金额称为销售额。

库存是历史事实，即使目录被软删除，仍保留其正库存批次。目录信息缺失时显示“目录信息缺失”，不隐藏库存。页面使用真实 `manufacturer` 标注“生产厂家”，不冒充尚未维护的“注册证持有人”。没有库位、效期、单位等信息时显示“未维护”。

公告表/发布模块尚未配置，接口返回 `noticeStatus=NOT_CONFIGURED`，页面显示数据源不可用；不显示演示公告，也不将不可用误报为真实的零条公告。

## 响应与分页

接口使用既有 `{code,message,data,timestamp}` 包装。概览 data 包含 `businessDate/generatedAt/timezone/scope/metrics/alerts/todoTotal/todos/trend/noticeStatus`，明确的 VO 与独立 frontend contract validator 一一对应。

批次接口参数：`page=1`、`pageSize=5`、`keyword=''`。page 范围1–1000000，pageSize 范围1–100，keyword 最多100字符。结果为 `data.{total,page,pageSize,records}`。分页、计数和搜索全部在数据库中执行；前端 remote 模式不再对单页结果二次分页或过滤。排序为效期升序（空效期排后）、批次ID升序，避免相同效期翻页顺序不稳定。超出末页返回空 records，保留真实 total。搜索参数绑定，使用字面子串匹配，不将用户输入当 SQL 或 LIKE 通配符。

批次 `id`、`reagentId` 与待办 `id` 始终为字符串，避免超过 JavaScript 安全整数范围后丢失精度。金额通过 BigDecimal 计算；可空金额显式序列化 null，覆盖全局 non_null 的字段省略行为。未知金额不能使用 `|| 0`。

参数值越界返回现有业务错误码40001，类型错误为HTTP400。未登录遵循现有401鉴权，账号停用/没有数据范围为40300。403表示无权限，不代表登录失效；前端不再因HTTP403删除登录态。

安全过滤链显式返回 JSON：缺失或无效/过期的 Access Token 为 HTTP401 + code40100；认证后无权限为 HTTP403 + code40300。修正了原默认配置将未登录也返回403的问题，否则前端无法可靠地区分登录过期与权限不足。401会清除失效登录态并返回登录页，403保留会话并展示权限提示。

## 页面健壮性

- 概览和批次独立加载。首次加载失败不绘制零值卡；刷新失败可保留上次概览，但明确标记旧结果。
- 批次失败展示错误和重试入口，不继续显示与当前查询不一致的旧列表。
- 搜索防抖300ms、旧请求取消和请求序号双重保护，迟到响应不能覆盖最新结果。卸载页面清理请求和定时器。
- 关键字段类型、分页结构、30天日期顺序、金额 null 语义在前端校验，契约错误进入错误态而非静默取默认值。
- 工作台采用页内错误提示；请求取消不弹错误消息。表格加载遮罩阻止在请求中的旧结果上继续操作。

## 验证

```powershell
# 隔离H2数据库的SQL/服务回归（6项）
cd D:\IVD\ivd-app
& 'D:\apache-maven-3.9.0\bin\mvn.cmd' -o test -pl ivd-analytics -am '-Dtest=WorkbenchServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
# 另含安全过滤链测试（4项，不启动业务数据库）
& 'D:\apache-maven-3.9.0\bin\mvn.cmd' -o test -pl ivd-server -am '-Dtest=SecurityResponseTest,WorkbenchServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
# 前端契约回归（5项）
cd D:\IVD\ivd-web
node.exe --test scripts/qa/workbench-contract.test.mjs
# 在独立测试浏览器登录后：真实数据及故障注入验收，不写数据库
npx.cmd --yes --package @playwright/cli playwright-cli -s=real-workbench run-code --filename=scripts/qa/workbench-real-ui.js
```

2026-09-08实库对账：正库存批次20、已过期2、90天内到期2、UDI待补全0、未闭环缺货2、待办申领1；近30天外部入库3200元，调拨出库0元，今日记录0。以上为当次数据库快照，后续随业务变化。

本次验证覆盖首页只读链路及相关错误处理，不代表历史财务分析、结算、自动审核等全部写流程已经通过完整业务验收。缺少源数据的分析项应继续明确标记不可用，不能复用旧接口的硬编码指标充当事实。
