import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { requiresReceiptTracking } from '../../src/utils/receivingLines.mjs'

test('only quantity-received material types may omit lot and expiry', () => {
  for (const materialType of ['CONSUMABLE', 'OTHER']) assert.equal(requiresReceiptTracking({ materialType }), false)
  for (const materialType of ['REAGENT', 'CALIBRATOR', 'QC_MATERIAL', undefined]) assert.equal(requiresReceiptTracking({ materialType }), true)
})

test('renewal approval uses the explicit endpoint and the standard modal compiles', () => {
  assert.match(readFileSync('src/api/license.js', 'utf8'), /\/renewal\/approve/)
  const filename = 'src/views/license/components/LicenseRenewalModal.vue'
  const source = readFileSync(filename, 'utf8')
  assert.match(source, /approveLicenseRenewal\(id, data\)/)
  assert.match(source, /form\.attachmentKey/)
  assert.match(source, /form\.reason/)
  const { descriptor, errors } = parse(source, { filename })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'license-renewal' })
  assert.deepEqual(compileTemplate({
    source: descriptor.template.content, filename, id: 'license-renewal',
    compilerOptions: { bindingMetadata: script.bindings }
  }).errors, [])
})
