import http from 'node:http'
import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { dirname, extname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), process.env.IVD_FRONTEND_DIST || '../dist')
const port = Number(process.env.IVD_FRONTEND_PORT || 5173)
const backend = new URL(process.env.IVD_BACKEND_URL || 'http://127.0.0.1:8080')
if (backend.protocol !== 'http:') throw new Error('IVD_BACKEND_URL must use http')
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.pdf': 'application/pdf' }
const index = resolve(root, 'index.html')
await stat(index) // Missing build must fail startup, not serve a blank application.
const server = http.createServer(async (request, response) => {
  const pathname = (request.url || '/').split('?')[0]
  if (pathname === '/api' || pathname.startsWith('/api/')) {
    const upstream = http.request({ hostname: backend.hostname, port: backend.port || 80,
      path: request.url, method: request.method, headers: { ...request.headers, host: backend.host } }, incoming => {
      response.writeHead(incoming.statusCode || 502, { ...incoming.headers, 'cache-control': 'no-store' })
      incoming.pipe(response)
      incoming.on('error', () => response.destroy())
    })
    upstream.setTimeout(30000, () => upstream.destroy(new Error('Backend timeout')))
    upstream.on('error', () => {
      if (!response.headersSent) { response.writeHead(502, { 'content-type': 'application/json', 'cache-control': 'no-store' }); response.end(JSON.stringify({ code: 50000, message: '后端服务暂不可用', data: null })) }
      else response.destroy()
    })
    request.on('aborted', () => upstream.destroy())
    response.on('close', () => { if (!response.writableEnded) upstream.destroy() })
    request.pipe(upstream)
    return
  }
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405, { allow: 'GET, HEAD' }); response.end(); return }
  try {
    const decoded = decodeURIComponent(pathname)
    if (decoded.includes('\0') || decoded.includes('\\') || decoded.split('/').some(part => part === '..' || part.startsWith('.'))) {
      response.writeHead(400); response.end(); return
    }
    let target = resolve(root, '.' + decoded)
    if (target !== root && !target.startsWith(root + sep)) { response.writeHead(403); response.end(); return }
    let info = await stat(target).catch(() => null)
    if (!info?.isFile()) {
      // History-mode routes fall back to HTML, missing assets must remain 404.
      if (extname(decoded) || decoded.startsWith('/assets/')) { response.writeHead(404); response.end(); return }
      target = index; info = await stat(index)
    }
    response.writeHead(200, { 'content-type': types[extname(target)] || 'application/octet-stream',
      'content-length': info.size, 'x-content-type-options': 'nosniff',
      'cache-control': target.includes(sep + 'assets' + sep) ? 'public, max-age=31536000, immutable' : 'no-cache' })
    if (request.method === 'HEAD') response.end()
    else createReadStream(target).on('error', () => response.destroy()).pipe(response)
  } catch {
    response.writeHead(400); response.end()
  }
})
server.requestTimeout = 35000
server.on('error', error => { console.error(`Frontend startup failed: ${error.code || error.message}`); process.exitCode = 1 })
server.listen(port, '127.0.0.1', () => console.log(`IVD production frontend: http://127.0.0.1:${server.address().port}`))
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close())
