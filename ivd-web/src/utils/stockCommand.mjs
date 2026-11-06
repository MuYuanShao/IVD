// 客户端索引不作安全校验；服务器独立保存SHA-256摘要及实际操作人。
function ordered(value) {
  if (value?.toJSON) return ordered(value.toJSON())
  if (Array.isArray(value)) return value.map(ordered)
  if (value && typeof value === 'object')
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, ordered(value[key])]))
  return value
}

function indexHash(value) {
  let hash = 0xcbf29ce484222325n
  for (let i = 0; i < value.length; i++)
    hash = BigInt.asUintN(64, (hash ^ BigInt(value.charCodeAt(i))) * 0x100000001b3n)
  return hash.toString(16)
}

function randomId() {
  return Array.from(globalThis.crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
}

/** 每个账号、机构、业务入口及资源同时只保留一个尚未确认的意图。 */
export function createStockCommandRunner({ scope, storage = () => globalThis.sessionStorage, newKey = randomId,
  onRecovered = () => {}, onConflict = async () => false, onCleanupFailure = () => {} }) {
  const running = new Map()
  function run(operation, identity, payload, send) {
    const slot = 'ivd-stock-command-v1:' + indexHash(JSON.stringify([scope(), operation, identity]))
    const signature = JSON.stringify(ordered(payload))
    const active = running.get(slot)
    if (active) {
      if (active.signature !== signature) return Promise.reject(new Error('上一操作仍在处理中，请等待确认后再修改内容'))
      return active.promise
    }
    let session, previous, key
    try {
      session = storage()
      previous = session.getItem(slot)
      key = previous === null ? newKey() : previous
      if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/.test(key)) throw new Error('invalid request key')
      // 先持久化随机标识才发请求；会话中不保存原始请求、备注或令牌。
      session.setItem(slot, key)
    } catch {
      return Promise.reject(new Error('无法保留业务重试标识，请启用会话存储并核对上次操作后重试'))
    }
    function forget() {
      try { if (session.getItem(slot) === key) session.removeItem(slot) }
      catch { onCleanupFailure() }
    }
    const promise = Promise.resolve().then(() => send(key)).then(result => {
      forget()
      if (previous !== null) onRecovered()
      return result
    }, async error => {
      // 只有服务器确认标识冲突、且用户明确核对后才清除；不会自动重发新业务。
      if (error.businessDetail === 'STOCK_COMMAND_CONFLICT' && await onConflict(error)) forget()
      throw error
    }).finally(() => running.delete(slot))
    running.set(slot, { signature, promise })
    return promise
  }
  return { run }
}
