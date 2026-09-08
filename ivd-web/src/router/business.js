/** Apply the UI shell without changing business URLs, guards or page services. */
const navigationGroups = {
  Catalog: '基础资料',
  License: '基础资料',
  ChangeLog: '基础资料',
  SupplierManage: '基础资料',
  Requisition: '采购申领',
  Shortage: '采购申领',
  Purchase: '采购申领',
  Inbound: '采购申领',
  Receiving: '采购申领',
  PickAndShip: '采购申领',
  DeptReceive: '采购申领',
  CentralWarehouse: '三级库存',
  DeptWarehouse: '三级库存',
  ExpiryWarning: '三级库存',
  GroupWarehouse: '三级库存',
  Consumption: '三级库存',
  Audit: '质量追溯',
  Rules: '质量追溯',
  Trace: '质量追溯',
  Settlement: '财务分析',
  Finance: '财务分析',
  Analytics: '财务分析',
  UserManagement: '系统管理',
  DeptManagement: '系统管理',
  WarehouseManagement: '系统管理',
  CatalogFieldOptionManagement: '系统管理',
}

export function withBusinessLayout(route) {
  if (route.path !== '/') return route
  const oldDashboard = route.children.find(
    (child) => child.name === 'Dashboard',
  )
  const children = route.children.map((child) => ({
    ...child,
    meta: {
      ...child.meta,
      navGroup: child.meta?.navGroup || navigationGroups[child.name],
    },
    ...(child.name === 'Dashboard'
      ? {
          component: () => import('@/views/workspace/DashboardView.vue'),
          meta: { ...child.meta, title: '首页', mock: true },
        }
      : {}),
  }))
  if (oldDashboard)
    children.push({
      ...oldDashboard,
      path: 'dashboard-live',
      name: 'LiveDashboard',
      meta: { title: '业务数据看板', navGroup: '财务分析' },
    })
  return {
    ...route,
    component: () => import('@/layout/BusinessLayout.vue'),
    meta: { ...route.meta, business: true },
    children,
  }
}

/** Demo cards reuse fixtures but lead to existing workflows in the business shell. */
export const businessDestinations = {
  '/workspace/trade/purchase': '/requisition',
  '/workspace/trade/inbound': '/receiving',
  '/workspace/trade/outbound': '/pick-and-ship',
  '/workspace/trade/shortage': '/shortage',
  '/workspace/central/inventory': '/central',
  '/workspace/central/query': '/central',
  '/workspace/central/department': '/dept',
  '/workspace/quality/expiry': '/expiry',
  // No matching production workflow exists; keep this fixture in its labeled demo page.
  '/workspace/quality/defective': '/workspace/quality/defective',
  '/workspace/analysis/overview': '/analytics',
}
