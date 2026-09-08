/** Deterministic demo fixtures; no network requests or production records. */
export const user = {
  name: '张晓敏',
  department: '检验科',
  role: '科室管理员',
  hospital: '示范医院',
}
export const statistics = [
  {
    title: '今日销售额',
    value: 128650.0,
    precision: 2,
    prefix: '¥',
    unit: '元',
    tone: 'orange',
    icon: 'money',
    comparison: '较昨日 +12.8%',
    path: '/workspace/analysis/overview',
  },
  {
    title: '今日售出数量',
    value: 1268,
    unit: '件',
    tone: 'teal',
    icon: 'cart',
    comparison: '较昨日 +8.2%',
    path: '/workspace/trade/outbound',
  },
  {
    title: '今日入库',
    value: 2356,
    unit: '件',
    tone: 'purple',
    icon: 'inbound',
    comparison: '已完成 18 笔入库',
    path: '/workspace/trade/inbound',
  },
  {
    title: '今日出库',
    value: 1842,
    unit: '件',
    tone: 'blue',
    icon: 'outbound',
    comparison: '已完成 26 笔出库',
    path: '/workspace/trade/outbound',
  },
]
export const todos = [
  {
    id: 'SQ202609080012',
    title: '检验科 · 常规耗材申领',
    status: '待审核',
    type: 'warning',
    progress: 25,
    amount: '¥ 12,680.00',
    time: '09:42',
    items: '一次性使用采血管等 8 项',
  },
  {
    id: 'CK202609080008',
    title: '门诊部 · 耗材配送',
    status: '待出库',
    type: 'primary',
    progress: 50,
    amount: '¥ 8,540.00',
    time: '09:28',
    items: '一次性使用输液器等 5 项',
  },
  {
    id: 'RK202609080006',
    title: '中心库 · 采购入库',
    status: '待验收',
    type: 'success',
    progress: 75,
    amount: '¥ 25,960.00',
    time: '09:16',
    items: '医用外科口罩等 12 项',
  },
  {
    id: 'SQ202609080009',
    title: '急诊科 · 紧急耗材申领',
    status: '待审核',
    type: 'warning',
    progress: 25,
    amount: '¥ 3,260.00',
    time: '08:56',
    items: '一次性使用无菌注射器等 3 项',
  },
]
export const alerts = [
  {
    title: '库存预警',
    value: 12,
    unit: '种',
    tone: 'orange',
    description: '低于安全库存',
    path: '/workspace/central/inventory',
  },
  {
    title: '批次效期',
    value: 8,
    unit: '批',
    tone: 'purple',
    description: '90 天内到期',
    path: '/workspace/quality/expiry',
  },
  {
    title: '不良品',
    value: 3,
    unit: '件',
    tone: 'blue',
    description: '待处理商品',
    path: '/workspace/quality/defective',
  },
  {
    title: '缺货提醒',
    value: 6,
    unit: '种',
    tone: 'teal',
    description: '及时补充库存',
    path: '/workspace/trade/shortage',
  },
]
export const notices = [
  {
    id: 1,
    title: '关于开展第三季度耗材盘点的通知',
    date: '09-08',
    category: '重要',
    content:
      '请各科室于 9 月 15 日前完成本季度耗材盘点，核对实物数量与库位信息，并提交盘点结果。本通知为演示内容。',
  },
  {
    id: 2,
    title: '医用耗材精细化管理培训安排',
    date: '09-06',
    category: '培训',
    content:
      '培训将围绕申领、验收、配送及批次追溯流程展开。请科室管理员提前整理实际操作问题。本通知为演示内容。',
  },
  {
    id: 3,
    title: '系统功能优化与维护公告',
    date: '09-05',
    category: '系统',
    content:
      '本次界面示例展示顶部导航、工作台与库存列表的交互方式。所有演示数据均保存在本地前端。',
  },
]
const names = [
  [
    '一次性使用真空采血管',
    '山东威高集团医用高分子制品股份有限公司',
    '5mL / 紫帽',
    '支',
  ],
  [
    '一次性使用无菌注射器',
    '上海康德莱企业发展集团股份有限公司',
    '5mL / 0.7mm',
    '支',
  ],
  ['医用外科口罩', '稳健医疗用品股份有限公司', '17.5cm × 9.5cm', '只'],
  ['一次性使用输液器', '江西洪达医疗器械集团有限公司', 'IS-G / 0.55mm', '套'],
  [
    '一次性使用医用橡胶检查手套',
    '江苏科邦医用乳胶器材有限公司',
    '中号 / 无粉',
    '双',
  ],
  ['C反应蛋白检测试剂盒', '九强生物技术股份有限公司', '200T / 盒', '盒'],
  ['无菌纱布块', '振德医疗用品股份有限公司', '10cm × 10cm / 8层', '包'],
]
export const products = Array.from({ length: 28 }, (_, index) => {
  const [name, holder, specification, unit] = names[index % names.length]
  return {
    id: `HC${String(index + 1).padStart(6, '0')}`,
    name,
    holder,
    specification,
    unit,
    expiry: `202${7 + (index % 2)}-${String(1 + (index % 12)).padStart(2, '0')}-28`,
    quantity: 120 + index * 47,
    location: `A${1 + (index % 3)}-0${1 + (index % 6)}-0${1 + (index % 4)}`,
    department: index % 2 ? '中心库' : '检验科',
    batch: `LOT202609${String(index + 1).padStart(2, '0')}`,
  }
})
export const trend = Array.from({ length: 30 }, (_, i) => ({
  date:
    i < 22
      ? `08-${String(i + 10).padStart(2, '0')}`
      : `09-${String(i - 21).padStart(2, '0')}`,
  inbound: +(2.2 + ((i * 7) % 13) * 0.31 + Math.floor(i / 9) * 0.35).toFixed(2),
  outbound: +(1.6 + ((i * 5) % 11) * 0.28 + Math.floor(i / 8) * 0.28).toFixed(
    2,
  ),
}))
export const moduleFixtures = {
  equipment: {
    columns: [
      ['name', '设备名称'],
      ['code', '设备编号'],
      ['department', '使用科室'],
      ['date', '下次保养'],
      ['status', '状态'],
    ],
    rows: [
      {
        id: 'E1',
        name: '全自动生化分析仪',
        code: 'EQ-001',
        department: '生化组',
        date: '2026-09-15',
        status: '运行正常',
      },
      {
        id: 'E2',
        name: '全自动血液分析仪',
        code: 'EQ-002',
        department: '临检组',
        date: '2026-09-12',
        status: '待保养',
      },
    ],
  },
  licenses: {
    columns: [
      ['name', '证照名称'],
      ['holder', '注册证持有人'],
      ['expiry', '有效期'],
      ['status', '状态'],
    ],
    rows: [
      {
        id: 'L1',
        name: '一次性使用真空采血管注册证',
        holder: names[0][1],
        expiry: '2028-06-30',
        status: '有效',
      },
      {
        id: 'L2',
        name: '医用外科口罩注册证',
        holder: names[2][1],
        expiry: '2027-03-31',
        status: '有效',
      },
    ],
  },
  alerts: {
    columns: [
      ['name', '预警事项'],
      ['category', '类型'],
      ['quantity', '待处理数量'],
      ['status', '状态'],
    ],
    rows: alerts.map((item, i) => ({
      id: `A${i}`,
      name: item.description,
      category: item.title,
      quantity: `${item.value} ${item.unit}`,
      status: '待处理',
    })),
  },
  orders: {
    columns: [
      ['id', '单据编号'],
      ['title', '业务事项'],
      ['amount', '金额'],
      ['status', '状态'],
    ],
    rows: todos,
  },
  interfaces: {
    columns: [
      ['name', '接口名称'],
      ['source', '来源系统'],
      ['time', '最后同步'],
      ['status', '状态'],
    ],
    rows: [
      {
        id: 'I1',
        name: '耗材基础信息同步',
        source: 'HIS',
        time: '2026-09-08 09:40',
        status: '同步成功',
      },
      {
        id: 'I2',
        name: '检验消耗记录同步',
        source: 'LIS',
        time: '2026-09-08 09:35',
        status: '同步成功',
      },
    ],
  },
}
