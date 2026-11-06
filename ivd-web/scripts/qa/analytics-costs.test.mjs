import test from 'node:test'
import assert from 'node:assert/strict'
import { costShares, displayCost } from '../../src/utils/analyticsCosts.mjs'

test('unknown money is distinct from recorded zero', () => {
  for (const value of [null, undefined, '', ' ', NaN, Infinity, false]) {
    assert.equal(displayCost(value), '未知')
  }
  assert.equal(displayCost('0.00'), '¥0.00')
})

test('incomplete or zero denominators do not produce percentages', () => {
  assert.deepEqual(costShares([{ amount: 10 }, { amount: null }], 'amount'), [null, null])
  assert.deepEqual(costShares([{ amount: 0 }], 'amount'), [null])
  assert.deepEqual(costShares([{ amount: 0 }, { amount: '10' }], 'amount'), [0, 1])
})
