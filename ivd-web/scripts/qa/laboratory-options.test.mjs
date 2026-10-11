import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { ResultType, IntervalKind, ReferenceSex, resultTypeOptions, intervalKindOptions, optionLabel } from '../../src/utils/laboratoryOptions.mjs'

test('public enum wire codes remain unchanged and unknown values are not mislabeled', () => {
  assert.deepEqual(Object.values(ResultType), ['NUMERIC', 'TEXT'])
  assert.deepEqual(Object.values(IntervalKind), ['REFERENCE', 'MEASUREMENT'])
  assert.deepEqual(Object.values(ReferenceSex), ['ANY', 'MALE', 'FEMALE'])
  assert.equal(optionLabel(resultTypeOptions, ResultType.NUMERIC), '定量')
  assert.equal(optionLabel(intervalKindOptions, IntervalKind.MEASUREMENT), '设备测量范围')
  assert.equal(optionLabel(intervalKindOptions, 'UNKNOWN'), '未知')
})

test('each route owns its view and all editors are independent compileable components', () => {
  for (const path of ['views/laboratory/LabTestItemsView.vue', 'views/laboratory/LabIntervalsView.vue', 'components/laboratory/LabTestItemEditorModal.vue', 'components/laboratory/LabIntervalEditorModal.vue', 'components/business/LabCatalogTable.vue']) {
    const source = readFileSync(new URL(`../../src/${path}`, import.meta.url), 'utf8')
    const { descriptor, errors } = parse(source, { filename: path })
    assert.deepEqual(errors, [])
    compileScript(descriptor, { id: path })
    assert.deepEqual(compileTemplate({ source: descriptor.template.content, filename: path, id: path }).errors, [])
    if (path.startsWith('views/')) { assert.ok(!source.includes('<el-dialog')); assert.ok(!source.includes('labMode')) }
  }
  const routes = readFileSync(new URL('../../src/router/business.js', import.meta.url), 'utf8')
  assert.ok(routes.includes('LabTestItemsView.vue'))
  assert.ok(routes.includes('LabIntervalsView.vue'))
  assert.ok(!routes.includes('LabCatalogView.vue'))
})
