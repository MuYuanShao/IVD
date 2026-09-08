/** A single route tree drives navigation, secondary entries and breadcrumbs. */
const modulePage = () => import('@/views/workspace/ModuleView.vue')
const group = (path, title, children) => ({
  path,
  name: `Workspace-${path}`,
  meta: { title },
  redirect: `/workspace/${path}/${children[0][0]}`,
  children: children.map(([slug, label, kind]) => ({
    path: slug,
    name: `Workspace-${path}-${slug}`,
    component: modulePage,
    meta: { title: label, kind: kind || 'products' },
  })),
})

export const workspaceRoute = {
  path: '/workspace',
  component: () => import('@/layout/WorkspaceLayout.vue'),
  redirect: '/workspace/home',
  meta: { title: '工作台', public: true },
  children: [
    {
      path: 'home',
      name: 'WorkspaceHome',
      component: () => import('@/views/workspace/DashboardView.vue'),
      meta: { title: '首页' },
    },
    group('equipment', '设备管理', [
      ['maintenance', '设备保养', 'equipment'],
      ['information', '设备信息', 'equipment'],
    ]),
    group('quality', '质量管理', [
      ['licenses', '证照管理', 'licenses'],
      ['expiry', '批次效期', 'alerts'],
      ['defective', '不良品管理', 'alerts'],
    ]),
    group('central', '中心库管理', [
      ['inventory', '库存管理'],
      ['query', '库存查询'],
      ['department', '科室库存查询'],
    ]),
    group('trade', '进销存中心', [
      ['purchase', '采购申请', 'orders'],
      ['inbound', '入库管理', 'orders'],
      ['outbound', '出库管理', 'orders'],
      ['shortage', '缺货管理', 'alerts'],
    ]),
    group('integration', '接口管理', [
      ['status', '接口运行状态', 'interfaces'],
      ['logs', '接口日志', 'interfaces'],
    ]),
    group('analysis', '数据分析', [
      ['overview', '运营分析', 'analysis'],
      ['stock', '库存分析', 'analysis'],
    ]),
  ],
}
