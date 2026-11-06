import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { localSettlementDate, settlementPeriod } from '../../src/utils/settlementDate.mjs'

test('settlement defaults to the local calendar day and derives the selected month', () => {
  assert.equal(localSettlementDate(new Date(2026, 10, 1, 0, 5)), '2026-11-01')
  assert.deepEqual(settlementPeriod('2024-02-29'), { date: '2024-02-29', yearMonth: '2024-02' })
})

test('cleared, Date-object and invalid calendar values cannot trigger settlement', () => {
  for (const value of [null, '', new Date(), '2026-02-29', '2026-11-31', '2026-1-01']) {
    assert.equal(settlementPeriod(value), null)
  }
})

test('finance picker and requests use the selected calendar date contract', () => {
  const view = readFileSync('src/views/finance/FinanceView.vue', 'utf8')
  assert.match(view, /v-model="settleDate"[^>]+value-format="YYYY-MM-DD"/)
  assert.match(view, /getFinanceStats\(period.date\)/)
  assert.match(view, /dailySettle\(period.date\)/)
  assert.match(view, /const ym = period.yearMonth/)
  assert.match(view, /monthlySettle\(ym\)/)
  assert.match(view, /const params = \{[^\n]*date: period.date/)
  assert.match(view, /if \(keyword\) params.keyword = keyword/)
  assert.match(view, /function applyFilters\(\) \{ page.value = 1/)
})
