import test from 'node:test'
import assert from 'node:assert/strict'
import {
  validateOverview,
  validateProducts,
  sumAmount,
  money,
} from '../../src/utils/workbenchContract.mjs'

const overview = () => ({
  businessDate: '2026-09-08',
  generatedAt: '2026-09-08 12:00:00',
  scope: '全院',
  timezone: 'Asia/Shanghai',
  metrics: {
    consumptionAmount: 0,
    consumedRecords: 0,
    inboundRecords: 0,
    transferRecords: 0,
    unpricedRecords: 0,
  },
  alerts: {
    expiredBatches: 0,
    expiringBatches: 0,
    pendingUdiBatches: 0,
    shortageOrders: 0,
  },
  todoTotal: 0,
  todos: [],
  trend: Array.from({ length: 30 }, (_, index) => ({
    date: new Date(Date.UTC(2026, 7, 10 + index)).toISOString().slice(0, 10),
    inbound: 0,
    outbound: 0,
    unpricedRecords: 0,
  })),
})
test('real empty values remain zero, without fabricated trend', () => {
  const data = validateOverview(overview())
  assert.equal(sumAmount(data.trend, 'inbound'), 0)
  assert.match(money(0), /0\.00/)
})
test('missing financial amount remains unknown, never coalesced to zero', () => {
  const data = overview()
  data.metrics.consumptionAmount = null
  data.trend[0].inbound = null
  data.trend[0].unpricedRecords = 1
  validateOverview(data)
  assert.equal(sumAmount(data.trend, 'inbound'), null)
  assert.equal(money(null), '金额未完整记录')
  delete data.metrics.consumptionAmount
  assert.throws(() => validateOverview(data), /consumptionAmount/)
})
test('invalid types and duplicate or incomplete trend dates reject the response', () => {
  const wrongType = overview()
  wrongType.metrics.consumedRecords = '0'
  assert.throws(() => validateOverview(wrongType))
  const wrongDate = overview()
  wrongDate.trend[1].date = wrongDate.trend[0].date
  assert.throws(() => validateOverview(wrongDate), /dateRange/)
  const missing = overview()
  missing.trend.pop()
  assert.throws(() => validateOverview(missing), /length/)
})
test('batch identity and fractional stock survive the pagination contract', () => {
  const result = {
    total: 11,
    page: 2,
    pageSize: 5,
    records: [
      {
        id: '9007199254740993',
        reagentId: '123',
        name: '试剂',
        quantity: 1.25,
      },
    ],
  }
  assert.equal(validateProducts(result).records[0].id, '9007199254740993')
  assert.equal(result.records[0].quantity, 1.25)
  result.records[0].id = 9007199254740993
  assert.throws(() => validateProducts(result), /batch/)
})
test('invalid pagination and totals cannot silently present a partial page as a full list', () => {
  assert.throws(() =>
    validateProducts({ total: 0, page: 0, pageSize: 5, records: [] }),
  )
  assert.throws(() =>
    validateProducts({
      total: 0,
      page: 1,
      pageSize: 5,
      records: [{ id: '1', reagentId: '1', name: 'a', quantity: 1 }],
    }),
  )
  assert.equal(
    validateProducts({ total: 0, page: 1, pageSize: 5, records: [] }).total,
    0,
  )
})
