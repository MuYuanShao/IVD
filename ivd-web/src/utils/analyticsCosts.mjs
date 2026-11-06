export function knownCost(value) {
  if (value == null || (typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

export function displayCost(value) {
  const amount = knownCost(value)
  return amount === null ? '未知' : `¥${amount.toFixed(2)}`
}

export function costShares(rows, key) {
  const values = rows.map(row => knownCost(row[key]))
  if (values.some(value => value === null)) return values.map(() => null)
  const total = values.reduce((sum, value) => sum + value, 0)
  return values.map(value => total > 0 ? value / total : null)
}
