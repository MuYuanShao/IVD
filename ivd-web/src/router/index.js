import { createRouter, createWebHistory } from 'vue-router'
import { workspaceRoute } from './workspace'
import { withBusinessLayout } from './business'

/**
 * IVD 一期 MVP 路由配置
 * 模块映射：
 *   M00 系统管理 — /system
 *   M01 试剂目录 — /catalog
 *   M03 证照管理 — /license
 *   M04 采购管理 — /procurement
 *   M06 三级库   — /inventory
 *   M08 上机消耗 — /consumption
 *   X2 结算管理 — /settlement
 *   M09 日清月结 — /finance
 *   S1 效益分析 — /analytics
 *   S3 自动审核 — /audit
 *   驾驶舱      — /dashboard
 */
const routes = [
  workspaceRoute,
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/views/layout/LayoutView.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '主任驾驶舱' } },
      { path: 'catalog', name: 'Catalog', component: () => import('@/views/catalog/CatalogList.vue'), meta: { title: '试剂目录' } },
      { path: 'license', name: 'License', component: () => import('@/views/license/LicenseList.vue'), meta: { title: '证照管理' } },
      { path: 'change-log', name: 'ChangeLog', component: () => import('@/views/license/ChangeLogView.vue'), meta: { title: '信息变更' } },
      { path: 'supplier', name: 'SupplierManage', component: () => import('@/views/license/SupplierManage.vue'), meta: { title: '供应商维护' } },
      // ── 进销存地基 · 采购管理（4 子页，v2 独立路由）──
      { path: 'requisition', name: 'Requisition', component: () => import('@/views/procurement/RequisitionList.vue'), meta: { title: '申领管理', group: '采购管理' } },
      { path: 'shortage', name: 'Shortage', component: () => import('@/views/procurement/ShortageList.vue'), meta: { title: '缺货单', group: '采购管理' } },
      { path: 'purchase', name: 'Purchase', component: () => import('@/views/procurement/PurchaseList.vue'), meta: { title: '采购订单', group: '采购管理' } },
      { path: 'inbound', name: 'Inbound', component: () => import('@/views/procurement/InboundView.vue'), meta: { title: '扫码验收', group: '采购管理' } },
      { path: 'receiving', name: 'Receiving', component: () => import('@/views/procurement/ReceivingView.vue'), meta: { title: '人工验收', group: '采购管理' } },
      { path: 'pick-and-ship', name: 'PickAndShip', component: () => import('@/views/procurement/PickAndShipView.vue'), meta: { title: '拣货发货', group: '采购管理' } },
      { path: 'dept-receive', name: 'DeptReceive', component: () => import('@/views/procurement/DepartmentReceiveView.vue'), meta: { title: '科室签收', group: '采购管理' } },
      // ── 进销存地基 · 三级库管理（4 子页，v2 独立路由）──
      { path: 'central', name: 'CentralWarehouse', component: () => import('@/views/inventory/CentralWarehouse.vue'), meta: { title: '中心库(一级)', group: '三级库' } },
      { path: 'dept', name: 'DeptWarehouse', component: () => import('@/views/inventory/DeptWarehouse.vue'), meta: { title: '科室库(二级)', group: '三级库' } },
      { path: 'expiry', name: 'ExpiryWarning', component: () => import('@/views/inventory/ExpiryWarning.vue'), meta: { title: '效期预警', group: '三级库' } },
      { path: 'warehouse', name: 'GroupWarehouse', component: () => import('@/views/inventory/GroupWarehouse.vue'), meta: { title: '组库(三级)', group: '三级库' } },
      // ★ 旧路由兼容重定向
      { path: 'procurement', redirect: '/requisition' },
      { path: 'inventory', redirect: '/central' },
      { path: 'consumption', name: 'Consumption', component: () => import('@/views/consumption/ConsumptionView.vue'), meta: { title: '上机消耗' } },
      { path: 'settlement', name: 'Settlement', component: () => import('@/views/settlement/SettlementList.vue'), meta: { title: '结算管理' } },
      { path: 'finance', name: 'Finance', component: () => import('@/views/finance/FinanceView.vue'), meta: { title: '日清月结' } },
      { path: 'analytics', name: 'Analytics', component: () => import('@/views/analytics/AnalyticsView.vue'), meta: { title: '效益分析' } },
      { path: 'audit', name: 'Audit', component: () => import('@/views/audit/AuditView.vue'), meta: { title: '自动审核' } },
      { path: 'rules', name: 'Rules', component: () => import('@/views/rules/RulesView.vue'), meta: { title: '规则引擎' } },
      { path: 'trace', name: 'Trace', component: () => import('@/views/trace/TraceView.vue'), meta: { title: 'UDI追溯' } },
      // ── 系统管理 · 基础数据（3 子页）──
      { path: 'system/users', name: 'UserManagement', component: () => import('@/views/system/UserManagement.vue'), meta: { title: '用户管理', group: '系统管理' } },
      { path: 'system/depts', name: 'DeptManagement', component: () => import('@/views/system/DeptManagement.vue'), meta: { title: '科室管理', group: '系统管理' } },
      { path: 'system/warehouses', name: 'WarehouseManagement', component: () => import('@/views/system/WarehouseManagement.vue'), meta: { title: '库房管理', group: '系统管理' } },
      { path: 'system/catalog-fields', name: 'CatalogFieldOptionManagement', component: () => import('@/views/system/CatalogFieldOptionManagement.vue'), meta: { title: '试剂目录字段配置', group: '系统管理' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: routes.map(withBusinessLayout)
})

/**
 * 全局导航守卫 — JWT 认证检查
 */
router.beforeEach((to, from, next) => {
  // 公开页面直接放行
  if (to.meta.public) {
    console.log('[Router Guard] 公开页面，直接放行:', to.path)
    return next()
  }

  const token = localStorage.getItem('ivd-token')
  console.log('[Router Guard] 检查认证:', to.path, 'token:', token ? '已提供' : '无')

  if (!token) {
    // 未登录，跳转登录页
    console.warn('[Router Guard] 无 token，重定向到登录页')
    return next({ name: 'Login', query: { redirect: to.fullPath } })
  }

  // 已登录，放行
  if (to.name === 'Login') {
    console.log('[Router Guard] 已登录用户访问登录页，重定向到 Dashboard')
    return next({ name: 'Dashboard' })
  }

  console.log('[Router Guard] 认证通过，放行:', to.path)
  next()
})

/** 全局后置钩子 — 更新页面标题 */
router.afterEach((to) => {
  if (to.meta.title) {
    document.title = `${to.meta.title} - IVD 智慧实验室`
  }
})

export default router
