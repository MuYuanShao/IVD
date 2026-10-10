import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

let child, backend, origin
before(async () => {
  backend = http.createServer((req, res) => {
    let body = ''
    req.on('data', chunk => { body += chunk })
    req.on('end', () => { res.setHeader('content-type', 'application/json'); res.end(JSON.stringify({ path: req.url, method: req.method, authorization: req.headers.authorization, body })) })
  })
  await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve))
  child = spawn(process.execPath, [fileURLToPath(new URL('../serve-production.mjs', import.meta.url))], {
    env: { ...process.env, IVD_FRONTEND_PORT: '0', IVD_BACKEND_URL: `http://127.0.0.1:${backend.address().port}` }, windowsHide: true,
  })
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Production server startup timed out')), 10000)
    child.once('error', reject)
    child.once('exit', code => { clearTimeout(timer); reject(new Error(`Server exited ${code}; run npm run build first`)) })
    child.stdout.on('data', chunk => { const match = String(chunk).match(/http:\/\/127\.0\.0\.1:\d+/); if (match) { origin = match[0]; clearTimeout(timer); resolve() } })
  })
})
after(async () => {
  child?.kill()
  if (backend) await new Promise(resolve => backend.close(resolve))
})
test('history routes serve production HTML without caching stale entry points', async () => {
  for (const route of ['/login', '/dashboard', '/system/depts']) {
    const response = await fetch(origin + route)
    assert.equal(response.status, 200)
    assert.equal(response.headers.get('cache-control'), 'no-cache')
    assert.match(await response.text(), /\/assets\//)
  }
})
test('hashed resources are cacheable but missing assets are not SPA HTML', async () => {
  const html = await (await fetch(origin)).text()
  const asset = html.match(/src="(\/assets\/[^" ]+\.js)"/)[1]
  const response = await fetch(origin + asset)
  assert.equal(response.status, 200)
  assert.match(response.headers.get('cache-control'), /immutable/)
  assert.match(response.headers.get('content-type'), /javascript/)
  assert.equal((await fetch(origin + '/assets/missing.js')).status, 404)
})
test('API proxy preserves credentials, body and query without caching', async () => {
  const response = await fetch(origin + '/api/v1/example?page=2', { method: 'POST', headers: { authorization: 'Bearer test-only', 'content-type': 'application/json' }, body: '{"value":3}' })
  assert.equal(response.headers.get('cache-control'), 'no-store')
  assert.deepEqual(await response.json(), { path: '/api/v1/example?page=2', method: 'POST', authorization: 'Bearer test-only', body: '{"value":3}' })
})
test('hidden files and encoded traversal are rejected', async () => {
  assert.equal((await fetch(origin + '/.env')).status, 400)
  assert.equal((await fetch(origin + '/%2e%2e%2fpackage.json')).status, 400)
  assert.equal((await fetch(origin + '/login', { method: 'POST' })).status, 405)
})
