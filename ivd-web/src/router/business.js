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
            meta: { ...child.meta, title: '首页', mock: false },
        }
      : {}),
  }))
  children.push({ path: 'system/research-projects', name: 'ResearchProjectSettings', component: () => import('@/views/system/ResearchProjectSettings.vue'), meta: { title: '科研项目设置', navGroup: '系统管理' } })
  children.push({ path: 'lab/items', name: 'LabTestItems', component: () => import('@/views/laboratory/LabTestItemsView.vue'), meta: { title: '检验项目', navGroup: '检验业务', feature: 'labCatalog', roles: ['SYSTEM_ADMIN', 'LAB_DIRECTOR', 'LAB_DOCTOR', 'LAB_TECH'] } })
  children.push({ path: 'lab/intervals', name: 'LabIntervals', component: () => import('@/views/laboratory/LabIntervalsView.vue'), meta: { title: '参考区间与测量范围', navGroup: '检验业务', feature: 'labCatalog', roles: ['SYSTEM_ADMIN', 'LAB_DIRECTOR', 'LAB_DOCTOR', 'LAB_TECH'] } })
  children.push({ path: 'lab/patients', name: 'LabPatients', component: () => import('@/views/laboratory/LabPatientsView.vue'), meta: { title: '患者档案', navGroup: '检验业务', feature: 'labPatients', roles: ['LAB_DIRECTOR', 'LAB_DOCTOR', 'LAB_TECH'] } })
  children.push({ path: 'lab/samples', name: 'LabSamples', component: () => import('@/views/laboratory/LabSamplesView.vue'), meta: { title: '样本与检测结果', navGroup: '检验业务', feature: 'labPatients', roles: ['LAB_DIRECTOR', 'LAB_DOCTOR', 'LAB_TECH'] } })
  children.push({ path: 'lab/results', name: 'LabResults', component: () => import('@/views/laboratory/LabResultsView.vue'), meta: { title: '患者 / 设备关联查询', navGroup: '检验业务', feature: 'labPatients', roles: ['LAB_DIRECTOR', 'LAB_DOCTOR', 'LAB_TECH'] } })
  children.push({ path: 'lab/devices', name: 'LabDevices', component: () => import('@/views/laboratory/LabDevicesView.vue'), meta: { title:'设备档案与项目能力',navGroup:'检验业务',feature:'labPatients',roles:['SYSTEM_ADMIN','LAB_DIRECTOR','LAB_DOCTOR','LAB_TECH'] } })
  children.push({ path: 'lab/reports', name: 'LabReports', component: () => import('@/views/laboratory/LabReportsView.vue'), meta: { title:'检验报告',navGroup:'检验业务',feature:'labPatients',roles:['LAB_DIRECTOR','LAB_DOCTOR','LAB_TECH'] } })
  children.push({ path: 'lab/imports', name: 'LabImports', component: () => import('@/views/laboratory/LabImportsView.vue'), meta: { title:'Excel 导入',navGroup:'检验业务',feature:'labPatients',roles:['SYSTEM_ADMIN','LAB_DIRECTOR','LAB_DOCTOR','LAB_TECH'] } })
  children.push({ path:'lab/evidence',name:'LabEvidenceTrace',component:()=>import('@/views/laboratory/LabEvidenceTraceView.vue'),meta:{ title:'批次 / 消耗临床追溯',navGroup:'检验业务',feature:'labPatients',roles:['LAB_DIRECTOR','LAB_DOCTOR','LAB_TECH'] } })
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
