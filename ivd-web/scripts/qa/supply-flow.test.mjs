import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { validSourceId, billingLabel } from '../../src/utils/settlementFlow.mjs'

test('source IDs retain bigint precision and invalid or rounded numeric values are rejected', () => {
  assert.equal(validSourceId('2109172573876547585'),true)
  assert.equal(validSourceId(2109172573876547585),false)
  assert.equal(validSourceId('9223372036854775808'),false)
  for (const value of ['',0,-1,'1.5','oops']) assert.equal(validSourceId(value),false)
})
test('unknown historical policies are never presented as already billed', () => {
  assert.equal(billingLabel({}), '历史来源／模式待核对')
  assert.equal(billingLabel({ settlementMode:'消耗结算',settlementStatus:'ACTIVE' }), '已生成消耗结算')
  assert.equal(billingLabel({ settlementStatus:'REVERSED' }), '消耗结算已冲销')
  assert.equal(billingLabel({ settlementMode:'领用结算' }), '按科室签收模式结算')
})
test('flow pages and protected settlement history modal compile without demo money', () => {
  for (const file of ['views/procurement/ReceivingView.vue','views/consumption/ConsumptionView.vue',
    'views/settlement/SettlementList.vue','views/settlement/components/SettlementHistoryModal.vue']) {
    const source=readFileSync(new URL(`../../src/${file}`,import.meta.url),'utf8')
    const { descriptor,errors }=parse(source,{filename:file});assert.deepEqual(errors,[])
    compileScript(descriptor,{id:file});assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:file,id:file}).errors,[])
    assert.ok(!source.includes('126.8万'))
  }
})
