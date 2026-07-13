/**
 * 表单校验规则
 */

/** 必填校验 */
export const required = (label) => ({ required: true, message: `请输入${label}`, trigger: 'blur' })

/** UDI-DI 格式校验（GS1/MA 标准） */
export const udiDi = { pattern: /^\d{14}$/, message: 'UDI-DI 格式不正确（需14位数字）', trigger: 'blur' }

/** 注册证号格式校验 */
export const registrationCertNo = {
  pattern: /^(国械注准|国械注进)\d{4}\d{8}$/,
  message: '注册证号格式不正确，例：国械注准20253123456',
  trigger: 'blur'
}

/** 正数校验 */
export const positive = { type: 'number', min: 0, message: '必须为正数', trigger: 'blur' }

/** 医保编码格式（27位） */
export const insuranceCode = {
  pattern: /^\d{27}$/,
  message: '医保编码为27位数字',
  trigger: 'blur'
}
