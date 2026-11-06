import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('license search sends the trimmed keyword through the existing list API', () => {
  const view = readFileSync('src/views/license/LicenseList.vue', 'utf8')
  const store = readFileSync('src/stores/license.js', 'utf8')
  assert.match(view, /const keyword = searchText\.value\.trim\(\)/)
  assert.match(view, /if \(keyword\) params\.keyword = keyword/)
  assert.match(view, /await licenseStore\.fetchList\(params\)/)
  assert.match(store, /await getLicenseList\(params\)\s+if \(requestId !== listRequest\) return/)
})
