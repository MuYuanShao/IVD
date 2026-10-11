import test from 'node:test'
import assert from 'node:assert/strict'
import { isFullAccessAdmin } from '../../src/utils/adminAccess.mjs'
import { allowedImportResources, importResources } from '../../src/utils/clinicalOptions.mjs'

test('fixed admin policy does not grant access by username or role alone', () => {
  assert.equal(isFullAccessAdmin({ username: 'admin', role: 'SYSTEM_ADMIN' }), true)
  assert.equal(isFullAccessAdmin({ username: 'other', role: 'SYSTEM_ADMIN' }), false)
  assert.equal(isFullAccessAdmin({ username: 'admin', role: 'LAB_TECH' }), false)
  assert.equal(isFullAccessAdmin(null), false)
})
test('fixed admin has every import resource without broadening other users', () => {
  assert.equal(allowedImportResources('SYSTEM_ADMIN', true).length, importResources.length)
  assert.equal(allowedImportResources('SYSTEM_ADMIN').some(r => r.clinical), false)
})
