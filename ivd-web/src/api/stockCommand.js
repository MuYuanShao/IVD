import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import { createStockCommandRunner } from '@/utils/stockCommand.mjs'

const runner = createStockCommandRunner({
  scope: () => {
    const user = useAuthStore().user
    if (!user?.userId || !user?.institutionId) throw new Error('请重新登录后提交业务')
    return `${user.institutionId}:${user.userId}`
  },
  onRecovered: () => ElMessage.info('上次未确认请求的结果已核对，请确认业务记录后再发起新操作'),
  onCleanupFailure: () => ElMessage.warning('业务结果已确认，但会话标识未能清理，请核对后再提交'),
  onConflict: async () => {
    try {
      await ElMessageBox.confirm('此标识已用于其他内容。请先核对消耗或验收记录；确认需要新增业务后，可解除旧标识并重新提交。', '核对未确认请求', {
        type: 'warning', confirmButtonText: '已核对，解除旧标识', cancelButtonText: '返回核对',
      })
      ElMessage.info('旧标识已解除，未自动提交新业务')
      return true
    } catch { return false }
  },
})

export async function stockCommand(operation, identity, config) {
  try {
    const token = localStorage.getItem('ivd-token')
    if (!token) throw new Error('请重新登录后提交业务')
    const clone = value => value === undefined ? undefined : JSON.parse(JSON.stringify(value))
    const data = clone(config.data), params = clone(config.params)
    return await runner.run(operation, String(identity ?? ''), { data, params }, key =>
      request({ ...config, data, params, stockCommandToken: token, headers: { ...config.headers, 'Idempotency-Key': key }, silentError: true }))
  } catch (error) {
    if (error.businessDetail !== 'STOCK_COMMAND_CONFLICT') ElMessage.error(error.message || '业务结果尚未确认，请保留原操作重试')
    throw error
  }
}
