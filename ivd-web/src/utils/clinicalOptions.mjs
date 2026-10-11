export const sampleStatuses = {
  COLLECTED: { label: '已采集', type: 'warning' },
  RECEIVED: { label: '已接收', type: 'success' },
  REJECTED: { label: '已拒收', type: 'danger' },
}
export const resultStatuses = {
  NORMAL: { label: '区间内', type: 'success' },
  LOW: { label: '偏低', type: 'warning' }, HIGH: { label: '偏高', type: 'warning' },
  ABNORMAL: { label: '异常提示', type: 'warning' },
  MISSING: { label: '待人工处理', type: 'info' }, CONFLICT: { label: '配置冲突', type: 'danger' },
  UNIT_MISMATCH: { label: '单位不一致', type: 'danger' },
  INVALID_RESULT: { label: '结果无效', type: 'danger' },
  NOT_APPLICABLE: { label: '不适用', type: 'info' },
}
export const executionModes = [
  { value: 'INSTRUMENT', label: '仪器检测' },
  { value: 'MANUAL', label: '手工检测' },
  { value: 'EXTERNAL', label: '外送检测' },
]
export function statusOption(options, code) { return options[code] || { label: '未知', type: 'info' } }
export function localDateTimeNow(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
export function clinicalError(error) { return error.response?.data?.message || error.message || '操作失败，请重试' }
export const reportStatuses = {
  DRAFT:{ label:'草稿',type:'info' },PENDING_REVIEW:{ label:'待复核',type:'warning' },REVIEWED:{ label:'已复核',type:'primary' },
  RETURNED:{ label:'已退回',type:'warning' },SIGNED:{ label:'已签发',type:'success' },REVOKED:{ label:'已撤销',type:'danger' },SUPERSEDED:{ label:'更正已替代',type:'info' },
}
export const reportActions = { submit:'提交复核',review:'人工复核',return:'退回编辑',sign:'业务签发',correct:'发起更正',revoke:'撤销报告' }
export const importResources = [
  { value:'PATIENTS',label:'患者档案',clinical:true },{ value:'SAMPLES',label:'样本',clinical:true },{ value:'RESULTS',label:'检测结果',clinical:true },{ value:'REPORTS',label:'报告及结果（整组事务）',clinical:true },
  { value:'ITEMS',label:'检验项目' },{ value:'INTERVALS',label:'参考区间 / 测量范围' },{ value:'DEVICES',label:'设备档案' },{ value:'CAPABILITIES',label:'设备项目能力' },{ value:'INSTITUTIONS',label:'检测机构',adminOnly:true },
]
export function allowedImportResources(role, fullAccessAdmin = false) { return importResources.filter(option=>fullAccessAdmin || (option.clinical ? ['LAB_TECH','LAB_DOCTOR','LAB_DIRECTOR'].includes(role) : option.adminOnly ? role==='SYSTEM_ADMIN' : ['SYSTEM_ADMIN','LAB_DIRECTOR'].includes(role))) }
