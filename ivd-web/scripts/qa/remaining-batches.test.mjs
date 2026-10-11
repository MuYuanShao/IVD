import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
test('remaining batches is a separate inventory page and dashboard no longer fetches products', () => {
  const filename='src/views/inventory/RemainingBatchView.vue'
  const { descriptor,errors }=parse(readFileSync(filename,'utf8'),{filename})
  assert.deepEqual(errors,[])
  const script=compileScript(descriptor,{id:'remaining'})
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename,id:'remaining',compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
  assert.match(readFileSync('src/router/business.js','utf8'),/name: 'RemainingBatches'.*navGroup: '三级库存'/)
  assert.match(readFileSync('src/api/inventory.js','utf8'),/url: '\/inventory\/remaining-batches'/)
  assert.match(readFileSync('src/views/workspace/DashboardView.vue','utf8'),/<product-table\s+v-if="isDemo"/)
  assert.match(readFileSync('src/composables/useWorkbenchData.js','utf8'),/Promise\.allSettled\(\[loadSummary\(\)\]\)/)
})
