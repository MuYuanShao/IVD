import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { saveBusinessFile } from '../../src/utils/businessDownload.mjs'

test('purchase plan views compile independently', () => {
  for (const filename of ['src/views/procurement/PurchasePlanView.vue','src/views/procurement/components/PurchasePlanEditorModal.vue','src/views/procurement/PurchaseList.vue','src/views/procurement/ShortageList.vue']) {
    const source=readFileSync(filename,'utf8')
    const { descriptor,errors }=parse(source,{filename})
    assert.deepEqual(errors,[])
    const script=compileScript(descriptor,{id:filename})
    assert.deepEqual(compileTemplate({source:descriptor.template.content,filename,id:filename,compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
  }
})
test('error payloads cannot be saved as an exported spreadsheet', () => {
  assert.throws(()=>saveBusinessFile(new Blob(['{}'],{type:'application/json'}),'orders.xlsx'),/有效/)
  assert.throws(()=>saveBusinessFile(new Blob([]),'orders.xlsx'),/未收到/)
})
test('plan workflow and explicit recalculate use actual resource URLs', () => {
  const api=readFileSync('src/api/procurement.js','utf8')
  for (const action of ['submit','approve','reject','convert']) assert.ok(api.includes('/'+action))
  assert.match(api,/smart-replenish\/recalculate[^\n]+method: 'post'/)
  const view=readFileSync('src/views/procurement/PurchaseList.vue','utf8')
  assert.match(view,/recalculateSmartReplenish\(/)
  assert.match(view,/plan\.id/)
  assert.match(view,/exportOrderReport\(/)
  assert.doesNotMatch(view,/即将下载/)
})
