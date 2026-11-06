import test from 'node:test'
import assert from 'node:assert/strict'
import { buildShipmentItems } from '../../src/utils/pickShipment.mjs'

const available = { id: '9007199254740993', reagentName: 'A', qtyRequested: 5, qtyFulfilled: 2, _batchId: '4', _pickQty: 2 }
const unavailable = { id: '8', reagentName: 'B', qtyRequested: 3, _pickQty: 0 }

test('only selected quantities are shipped, leaving unavailable items out', () => {
  assert.deepEqual(buildShipmentItems([available, unavailable]), [
    { reqItemId: available.id, sourceBatchId: '4', pickQty: 2 }
  ])
})

test('empty shipments and invalid selected lines are rejected', () => {
  assert.throws(() => buildShipmentItems([unavailable]), /至少/)
  assert.throws(() => buildShipmentItems([{ ...available, _batchId: null }]), /选择批次/)
  for (const qty of [-1, Infinity, 4]) {
    assert.throws(() => buildShipmentItems([{ ...available, _pickQty: qty }]), /数量无效/)
  }
})
