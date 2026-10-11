// Public wire codes stay compatible with the backend domain enums.
export const ResultType = Object.freeze({ NUMERIC: 'NUMERIC', TEXT: 'TEXT' })
export const IntervalKind = Object.freeze({
  REFERENCE: 'REFERENCE',
  MEASUREMENT: 'MEASUREMENT',
})
export const ReferenceSex = Object.freeze({
  ANY: 'ANY',
  MALE: 'MALE',
  FEMALE: 'FEMALE',
})
export const resultTypeOptions = Object.freeze([
  { value: ResultType.NUMERIC, label: '定量' },
  { value: ResultType.TEXT, label: '定性' },
])
export const intervalKindOptions = Object.freeze([
  { value: IntervalKind.REFERENCE, label: '正常参考区间' },
  { value: IntervalKind.MEASUREMENT, label: '设备测量范围' },
])
export const referenceSexLabels = Object.freeze({
  [ReferenceSex.ANY]: '不限',
  [ReferenceSex.MALE]: '男',
  [ReferenceSex.FEMALE]: '女',
})
export const optionLabel = (options, code) =>
  options.find((option) => option.value === code)?.label || '未知'
