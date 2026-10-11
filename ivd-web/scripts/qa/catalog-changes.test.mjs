import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

test('catalog history entry, draft warning and change detail components compile', () => {
  for (const file of ['views/catalog/CatalogList.vue', 'views/catalog/components/EditReagentDialog.vue',
    'views/catalog/components/CatalogDetailDialog.vue', 'views/license/ChangeLogView.vue',
    'views/license/components/ChangeDetailsModal.vue']) {
    const source = readFileSync(new URL(`../../src/${file}`, import.meta.url),'utf8')
    const { descriptor, errors } = parse(source, { filename:file })
    assert.deepEqual(errors,[])
    compileScript(descriptor,{ id:file })
    assert.deepEqual(compileTemplate({ source:descriptor.template.content, filename:file,id:file }).errors,[])
  }
  const history = readFileSync(new URL('../../src/views/license/ChangeLogView.vue',import.meta.url),'utf8')
  assert.match(history,/ChangeDetailsModal/)
  assert.match(history,/route.query.keyword/)
})
