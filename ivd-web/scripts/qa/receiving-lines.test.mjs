import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stageOrderReceipt, receiptUdi, directReceiptWarehouse } from '../../src/utils/receivingLines.mjs'

const row = { itemId: '9007199254740993', reagentId: '12', remaining: 3, receiveQty: 0, unitPrice: 10 }
const input = { reagentId: 12, receiveQty: 2, batchNo: 'LOT', udi: '  01234567890128  ', unitPrice: 99 }

test('direct receiving refuses mixed warehouses without losing bigint precision', () => {
  const id = '9007199254740993'
  assert.equal(directReceiptWarehouse([{ warehouseId: id }, { warehouseId: id }]), id)
  assert.equal(directReceiptWarehouse([{}, { warehouseId: null }]), undefined)
  assert.throws(() => directReceiptWarehouse([{ warehouseId: id }, { warehouseId: '9007199254740994' }]), /同一个中心库/)
  assert.throws(() => directReceiptWarehouse([{ warehouseId: id }, {}]), /统一库房/)
})

test('order staging preserves the real line identifier and price and carries UDI', () => {
  const [staged] = stageOrderReceipt([row], [input])
  assert.equal(staged.itemId, row.itemId)
  assert.equal(staged.unitPrice, row.unitPrice)
  assert.equal(staged.batchNo, 'LOT')
  assert.equal(receiptUdi(staged), '01234567890128')
  assert.equal(row.receiveQty, 0)
  assert.equal(receiptUdi({ udi: ' ' }), undefined)
})

test('ambiguous, excess, duplicate and non-order receipts are refused without altering the input', () => {
  assert.throws(() => stageOrderReceipt([row, { ...row, itemId: '2' }], [input]), /唯一/)
  assert.throws(() => stageOrderReceipt([row], [{ ...input, receiveQty: 4 }]), /剩余/)
  assert.throws(() => stageOrderReceipt([row], [input, input]), /已有/)
  assert.throws(() => stageOrderReceipt([row], [{ ...input, reagentId: 13 }]), /匹配/)
  assert.equal(row.receiveQty, 0)
})

test('both submission branches send the staged UDI and order submissions use real line IDs', () => {
  const source = readFileSync('src/views/procurement/ReceivingView.vue', 'utf8')
  assert.match(source, /itemId: item\.itemId/)
  assert.match(source, /udiDi: receiptUdi\(item\)/)
  assert.match(source, /udiDi: receiptUdi\(i\)/)
  assert.match(source, /await loadReceivingOrder\(orderId\)/)
})
