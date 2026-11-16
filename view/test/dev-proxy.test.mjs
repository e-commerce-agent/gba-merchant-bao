import assert from 'node:assert/strict'
import { createServer as createHttpServer } from 'node:http'
import { once } from 'node:events'
import { test } from 'node:test'
import { createServer } from 'vite'

test('Vite preserves browser Host and Origin through the API proxy', async () => {
  const upstream = createHttpServer((req, res) => {
    res.setHeader('Content-Type', 'application/json')
    res.end(JSON.stringify({ host: req.headers.host, origin: req.headers.origin,
      authorization: req.headers.authorization, path: req.url, method: req.method }))
  })
  upstream.listen(0, '127.0.0.1')
  await once(upstream, 'listening')
  const previous = process.env.VITE_PROXY_TARGET
  process.env.VITE_PROXY_TARGET = `http://127.0.0.1:${upstream.address().port}`
  let vite
  try {
    vite = await createServer({ server: { port: 0, strictPort: false } })
    await vite.listen()
    const port = vite.httpServer.address().port
    for (const hostname of ['127.0.0.1', 'localhost']) {
      const host = `${hostname}:${port}`
      const response = await fetch(`http://${host}/api/v1/chat/sessions`, {
        method: 'POST', headers: { Host: host, Origin: `http://${host}`,
          Authorization: 'Bearer proxy-test-only', 'Content-Type': 'application/json' }, body: '{}'
      })
      assert.equal(response.status, 200)
      const received = await response.json()
      assert.equal(received.host, host, 'rewriting Host turns browser same-origin POST into backend cross-origin')
      assert.equal(received.origin, `http://${host}`)
      assert.equal(received.authorization, 'Bearer proxy-test-only')
      assert.equal(received.path, '/api/v1/chat/sessions')
      assert.equal(received.method, 'POST')
    }
    const response = await fetch(`http://127.0.0.1:${port}/api/v1/chat/sessions`, {
      method: 'POST', headers: { Origin: 'https://evil.example' }, body: '{}'
    })
    assert.equal((await response.json()).origin, 'https://evil.example',
      'an untrusted Origin must reach the backend unchanged for rejection')
  } finally {
    if (previous === undefined) delete process.env.VITE_PROXY_TARGET
    else process.env.VITE_PROXY_TARGET = previous
    if (vite) await vite.close()
    await new Promise(resolve => upstream.close(resolve))
  }
})
