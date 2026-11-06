import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

test('department receiving binds the requisition contract and compiles', () => {
  const filename = 'src/views/procurement/DepartmentReceiveView.vue'
  const source = readFileSync(filename, 'utf8')
  assert.match(source, /prop="deptName"/)
  assert.match(source, /prop="shippedQty"/)
  assert.match(source, /Array.isArray\(row.items\) \? row.items.length : '—'/)
  assert.doesNotMatch(source, /prop="(?:fromDeptName|fulfilledQty|itemCount)"/)
  const { descriptor, errors } = parse(source, { filename })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'department-receiving' })
  assert.deepEqual(compileTemplate({
    source: descriptor.template.content, filename, id: 'department-receiving',
    compilerOptions: { bindingMetadata: script.bindings }
  }).errors, [])
})
