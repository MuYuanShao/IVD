import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

test('trace patient section is gated and distinguishes denied/ambiguous evidence from an empty list', () => {
  const filename='src/views/trace/TraceView.vue'
  const source=readFileSync(filename,'utf8')
  const {descriptor,errors}=parse(source,{filename})
  assert.deepEqual(errors,[])
  const script=compileScript(descriptor,{id:'resource-boundary'})
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename,id:'resource-boundary',compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
  assert.match(source,/v-if="traceResult && canReadLegacyPatients"/)
  assert.match(source,/if \(!canReadLegacyPatients.value\) return/)
  assert.match(source,/d.batchNo && canReadLegacyPatients.value/)
  assert.match(source,/无法安全展示患者关联/)
  assert.match(readFileSync('src/api/inventory.js','utf8'),/trace\/patients\/\$\{encodeURIComponent\(batchNo\)\}/)
})
