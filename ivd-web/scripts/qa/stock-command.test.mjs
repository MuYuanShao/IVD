import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { createStockCommandRunner } from '../../src/utils/stockCommand.mjs'

function setup() {
  const entries = new Map()
  const session = { getItem: key => entries.get(key) ?? null, setItem: (key, value) => entries.set(key, value), removeItem: key => entries.delete(key) }
  let counter = 0
  const options = { scope: () => '1:1001', storage: () => session, newKey: () => `qa-command-${++counter}` }
  return { entries, options, runner: createStockCommandRunner(options) }
}

test('lost response keeps the same random key across reload and the next acknowledged operation gets a new one', async () => {
  const { entries, options, runner } = setup()
  const keys = []
  await assert.rejects(runner.run('CONSUME', '42', { qty: 2, remark: '不应存储的原始备注' }, key => { keys.push(key); throw new Error('timeout') }), /timeout/)
  assert.equal(entries.size, 1)
  assert.equal(JSON.stringify([...entries]).includes('不应存储'), false)
  const reloaded = createStockCommandRunner(options)
  await reloaded.run('CONSUME', '42', { qty: 2, remark: '不应存储的原始备注' }, key => keys.push(key))
  assert.equal(entries.size, 0)
  await reloaded.run('CONSUME', '42', { qty: 2, remark: '不应存储的原始备注' }, key => keys.push(key))
  assert.equal(keys[0], keys[1]); assert.notEqual(keys[1], keys[2])
})

test('one in-flight request coalesces double submission and refuses edited input until it settles', async () => {
  const { runner } = setup()
  let resolve, sent = 0
  const send = () => { sent++; return new Promise(done => { resolve = done }) }
  const first = runner.run('ORDER_RECEIVE', '88', { qty: 1, batch: 'A' }, send)
  const second = runner.run('ORDER_RECEIVE', '88', { batch: 'A', qty: 1 }, send)
  assert.equal(first, second)
  await assert.rejects(runner.run('ORDER_RECEIVE', '88', { qty: 2, batch: 'A' }, send), /仍在处理中/)
  assert.equal(sent, 1)
  resolve('ok'); assert.equal(await second, 'ok')
})

test('an edited retry uses the existing key, and conflict confirmation never auto-submits a new operation', async () => {
  const { entries, options, runner } = setup()
  let oldKey
  await assert.rejects(runner.run('DIRECT_RECEIVE', 'direct-form', { qty: 1 }, key => { oldKey = key; throw new Error('timeout') }))
  const conflict = Object.assign(new Error('conflict'), { businessDetail: 'STOCK_COMMAND_CONFLICT' })
  await assert.rejects(runner.run('DIRECT_RECEIVE', 'direct-form', { qty: 2 }, key => { assert.equal(key, oldKey); throw conflict }))
  assert.equal(entries.size, 1)
  let attempts = 0
  const confirmed = createStockCommandRunner({ ...options, onConflict: async () => true })
  await assert.rejects(confirmed.run('DIRECT_RECEIVE', 'direct-form', { qty: 2 }, key => { attempts++; assert.equal(key, oldKey); throw conflict }))
  assert.equal(attempts, 1); assert.equal(entries.size, 0)
  await confirmed.run('DIRECT_RECEIVE', 'direct-form', { qty: 2 }, key => assert.notEqual(key, oldKey))
})

test('different authenticated scopes do not reuse a pending key', async () => {
  const { options } = setup()
  let scope = '1:1001', firstKey
  const runner = createStockCommandRunner({ ...options, scope: () => scope })
  await assert.rejects(runner.run('CONSUME', '42', { qty: 2 }, key => { firstKey = key; throw new Error('timeout') }))
  scope = '2:1001'
  await runner.run('CONSUME', '42', { qty: 2 }, key => assert.notEqual(key, firstKey))
  scope = '1:1001'
  await runner.run('CONSUME', '42', { qty: 2 }, key => assert.equal(key, firstKey))
})

test('unavailable session storage fails before the request is sent', async () => {
  const runner = createStockCommandRunner({ scope: () => '1:1001', storage: () => { throw new Error('blocked') } })
  await assert.rejects(runner.run('CONSUME', '42', {}, () => assert.fail('must not send')), /会话存储/)
})

test('all four protected routes supply a key and receiving forms retain unconfirmed rows', () => {
  const consumption = readFileSync('src/api/consumption.js', 'utf8')
  const procurement = readFileSync('src/api/procurement.js', 'utf8')
  assert.match(consumption, /stockCommand\('CONSUME', batchId/)
  assert.match(consumption, /stockCommand\('BATCH_CONSUME', batchNo/)
  assert.match(procurement, /stockCommand\('ORDER_RECEIVE', data\?\.itemId/)
  assert.match(procurement, /stockCommand\('DIRECT_RECEIVE', 'direct-form'/)
  assert.match(readFileSync('src/api/stockCommand.js', 'utf8'), /'Idempotency-Key': key/)
  const filename = 'src/views/procurement/ReceivingView.vue'
  const source = readFileSync(filename, 'utf8')
  assert.match(source, /验收未确认.*break/)
  assert.match(source, /if \(!f\) try/)
  assert.match(source, /supplierId: directSupplierId\.value/)
  assert.match(source, /filter\(s => s\.isActive === true\)/)
  assert.doesNotMatch(source, /supplierId:\s*1\b/)
  const { descriptor, errors } = parse(source, { filename }); assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'stock-command' })
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename, id: 'stock-command', compilerOptions: { bindingMetadata: script.bindings } }).errors, [])
})
