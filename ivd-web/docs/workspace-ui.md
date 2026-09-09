# 医用耗材精益管理工作台接入说明

## 运行与范围

```powershell
cd D:\IVD\ivd-web
npm.cmd ci
npm.cmd run dev
```

业务系统入口为 `http://127.0.0.1:5173/login`，登录后进入新版 `/dashboard`。独立演示入口为 `http://127.0.0.1:5173/workspace/home`。生产构建运行 `npm.cmd run build`，部署服务器需将前端路由回退到 `index.html`。

本次交付同时覆盖正式业务外壳和独立 mock 工作区：

- `/dashboard` 使用新版三栏 Dashboard，已接入真实台账统计、预警、申领待办与批次分页；详见 [真实数据契约](workbench-data-contract.md)。原业务看板组件保留在 `/dashboard-live`，其中历史分析指标不作为新首页事实来源。
- 既有采购、库存、目录、系统管理等 URL、表单、业务接口和登录鉴权保持不变，改由统一顶部导航外壳承载。业务页面显示“业务工作区”，不混淆真实数据与演示指标。认证用户信息和退出登录仍使用原 Auth Store；首页点击快捷入口会进入对应的原业务页面。
- `/workspace/**` 无需登录，全程不调用业务 API；用于独立展示、组件验收与模块 mock 示例。这里的用户信息也是 mock。
- 初版布局仅涉及前端；后续真实数据接入新增 Analytics 只读查询接口，不修改原业务写流程。演示偏好保存在 Pinia 内存中，刷新重置。

参考地址为用户提供的 FLI/FLY 供应链系统。实际浏览已到达登录页，但登录需要验证码，未取得登录后的页面。因此当前布局依据用户描述实现，**未完成与参考系统登录后截图的逐像素核对**。未读取、复制其内部组件代码或保存其账号密码。

## 目录与职责

```text
src/layout/
  WorkspaceLayout.vue       el-container 画布、用户弹窗
  BusinessLayout.vue        复用画布、启用业务用户与退出入口
  TopBar.vue                 品牌、水平 el-menu、用户入口
  SubMenu.vue                路由驱动入口条、溢出滚动控制
  AppMain.vue                面包屑、1200px 主内容、页脚
src/components/business/
  StatisticCard.vue          指标卡
  AlertCard.vue              预警卡
  TodoList.vue               待办与进度
  NoticeList.vue             公告时间线
  TrendChart.vue             ECharts 折线/柱状图
  ProductTable.vue           商品搜索、分页、详情弹窗
src/router/workspace.js     演示菜单/路由树
src/router/business.js      业务布局适配、路由 meta 分组、快捷入口映射
src/composables/useWorkspaceNavigation.js
src/styles/tokens.scss      工作区与浮层的主题变量
src/styles/business.scss    既有表格、卡片、工具条外观适配
src/stores/workspace.js     用户、常用功能、趋势时间范围
src/mock/workspace.js       固定 mock 数据
src/views/workspace/
  DashboardView.vue          三栏 Dashboard 组合
  ModuleView.vue             各模块示例列表/分析页
```

## 新增页面与菜单

在 `src/router/workspace.js` 的对应分组 `children` 中新增路由：

```js
{
  path: 'stocktake',
  name: 'Workspace-central-stocktake',
  component: () => import('@/views/workspace/StocktakeView.vue'),
  meta: { title: '库存盘点' }
}
```

挂载在 `central` 下后，访问地址为 `/workspace/central/stocktake`。主导航下拉菜单、当前模块高亮、二级菜单、面包屑和页面标题自动更新，无需修改 Layout。分组本身有 `meta.title` 和默认子页 `redirect`；菜单项 `meta.hidden: true` 可隐藏入口。当前模型支持一级模块与二级页面；若需更深层菜单，应把 TopBar 的子项递归渲染。

仅演示通用列表时，可以在 `group('central', '中心库管理', [...])` 参数里添加 `['stocktake', '库存盘点', 'products']`。`meta.kind` 选择 ModuleView 中的演示数据类型，不代表真实业务实现。

首页入口条展示所有可用子页；进入模块后只展示该模块的子页。入口条超宽时显示左右滚动按钮，支持鼠标、键盘聚焦及触控横向滚动。

## 新增和接入业务页面

正式页面继续在 `src/router/index.js` 的 `/` 子路由里注册，设置 `meta.title` 和 `meta.navGroup`。无需修改 Layout：

```js
{
  path: 'stocktake',
  name: 'Stocktake',
  component: () => import('@/views/inventory/StocktakeView.vue'),
  meta: { title: '库存盘点', navGroup: '三级库存' }
}
```

`withBusinessLayout` 在创建路由时给既有页面补充默认 `meta.navGroup`，优先保留显式配置；`useWorkspaceNavigation` 根据这些 metadata 生成菜单、二级入口和面包屑。没有 navGroup 的页面作为独立一级入口。业务路径和路由 name 不变，旧 `/procurement`、`/inventory` 重定向仍生效。

不要把真实业务页挂到公开 `/workspace` 分组。若确需在该分组内接入认证页面，子级必须显式设置 `meta.public: false`；更推荐直接注册在正式业务路由树。

真实接口仍统一由 `src/api/*.js` 发出。新增展示组件只接收 props 并抛出事件，不依赖 Axios、路由或业务 Store。ProductTable 自带本地搜索和分页；接入服务端分页时应将这两项状态上移到容器，避免对单页结果再次筛选。

## 组件约定

| 组件          | 主要 props                                                            | 事件         |
| ------------- | --------------------------------------------------------------------- | ------------ |
| StatisticCard | title, value(Number), precision, prefix, unit, tone, icon, comparison | select       |
| AlertCard     | title, value, unit, description, tone                                 | select       |
| TodoList      | items: id/title/status/type/progress/time                             | select(item) |
| NoticeList    | items: id/title/date/category                                         | select(item) |
| TrendChart    | title, summary, labels, series, height, unit, variant                 | 无           |
| ProductTable  | title, items, pageSize, showMore                                      | more         |

`tone` 为 `orange / teal / purple / blue`；StatisticCard 的 `icon` 为 `money / cart / inbound / outbound`。TrendChart 的 `series` 格式为 `[{ name: '入库金额', data: [1, 2, 3] }]`，`labels` 与 data 一一对应，`variant` 为 `line / bar`。图表通过 ResizeObserver 适应容器尺寸，在卸载时销毁实例与观察器，空数组显示空状态，`summary` 提供可访问的文本摘要。

```vue
<template>
  <statistic-card
    title="今日入库"
    :value="2356"
    unit="件"
    tone="purple"
    icon="inbound"
    comparison="已完成 18 笔入库"
    @select="router.push('/workspace/trade/inbound')"
  />
</template>
```

## Design tokens 与响应式

主题集中在 `src/styles/tokens.scss`：品牌青绿 `#009688`、辅助青绿 `#2EC4B6`，统计色为橙 `#E8913C`、青 `#23AAA1`、紫 `#8A7AC8`、蓝 `#528DCE`；背景 `#F3F6F7`，卡片白底，间距 16px，圆角 6px，主内容最大宽度 1200px。统计卡颜色与图表调色板分开；图表遵守仓库规定的 ECharts 五色序列。

CSS 变量作用在 `.scm-theme` 内，同时覆盖 Element Plus 的主题变量。弹窗/下拉浮层使用 `class="scm-theme"` 或 `popper-class="scm-theme"`，防止 Teleport 后丢失主题。该主题不修改既有页面的根变量。

桌面 ≥992px 为 `7 / 10 / 7` 三栏；1024px 压缩品牌文字和局部间距；768–991px 为两栏，右栏移至下一排；更小屏为单栏，统计卡两列。宽表格在自身区域内横向滚动，不推动整个文档。建议主要在 ≥1280px 使用。

## 验收方法

1. 三种桌面宽度检查导航、四张统计卡、三栏、商品表格、页脚。
2. 点击主导航下拉、二级入口；检查 URL、高亮、面包屑；刷新子页。
3. 切换近7天/近30天，确认曲线和总额同时改变。
4. 打开待办、公告、商品详情，按 Esc 关闭。
5. 管理常用功能，取消所有选项、重新添加；用户菜单重置偏好。
6. 搜索商品、查看空结果、翻页后搜索，确认页码重置。
7. 开发者工具 Network 检查 `/workspace/**` 未发送 `/api/` 请求。

可重复的浏览器验收脚本位于 `scripts/qa/workspace-ui.js`，通过 Playwright CLI 执行：

```powershell
npx.cmd --yes --package @playwright/cli playwright-cli -s=ivd-local open http://127.0.0.1:5173/workspace/home
New-Item -ItemType Directory -Path output/playwright -Force
npx.cmd --yes --package @playwright/cli playwright-cli -s=ivd-local run-code --filename=scripts/qa/workspace-ui.js
```

截图输出到 `output/playwright/workspace-{1440,1280,1024}.png`。

正式业务回归脚本为 `scripts/qa/business-ui.js`：在独立测试浏览器先登录本地测试账号，再运行该脚本。脚本遍历当前首页入口条中的业务页面、检查 1280/1024 宽度、打开并关闭申领弹窗、不提交申请，最后退出并验证登录保护。结果会返回 JavaScript 错误、失败接口和写请求列表。不要使用正在操作业务的浏览器会话运行，因为脚本最后会退出登录。

```powershell
npx.cmd --yes --package @playwright/cli playwright-cli -s=ivd-business open http://127.0.0.1:5173/login --headed
# 在测试浏览器完成登录后执行：
npx.cmd --yes --package @playwright/cli playwright-cli -s=ivd-business run-code --filename=scripts/qa/business-ui.js
```

2026-09-08 当前工作树验收：28 个业务导航入口可打开；首页、目录、申领、中心库在 1280/1024 下无文档横向溢出；无 JavaScript 异常、失败 API 或业务写请求；退出后的访问会重定向到登录。新建申领和展开明细另行完成动画结束后的截图检查。上述为 UI 与只读查询验证，不代表采购、审核、结算等写流程已重新验收。
