// Explicit dev acceptance: writes only new, labelled chat data and revokes its own login.
// node scripts/verify-chat-dev.mjs (requires dev SMS debugCode and real model configuration).
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const base = process.env.CHAT_TEST_BASE_URL || 'http://127.0.0.1:5173'
const origin = new URL(base).origin
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname), 'dev acceptance must use loopback')
const report = { date: new Date().toISOString(), base, operations: [], events: [] }
let token, refreshToken
async function call(method, path, body, expected = 200, authenticated = true, requestOrigin = origin) {
  const requestId = `chat-dev-${crypto.randomUUID()}`
  const response = await fetch(`${base}/api/v1${path}`, { method, signal: AbortSignal.timeout(200000),
    headers: { Origin: requestOrigin, 'X-Request-Id': requestId, Accept: 'application/json, text/event-stream',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(authenticated && token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  report.operations.push({ method, path, status: response.status, requestId })
  assert.equal(response.status, expected, `${method} ${path}: expected ${expected}, got ${response.status}`)
  return response
}
async function data(...args) { return (await (await call(...args)).json()).data }
try {
  await call('POST', '/chat/sessions', {}, 401, false)
  await call('POST', '/chat/sessions', {}, 403, false, 'https://evil.example')
  const phone = process.env.CHAT_TEST_PHONE || '13800138000'
  const auth = '/stores/M_3C_DEMO/auth'
  const sms = await data('POST', `${auth}/sms-codes`, { phone }, 200, false)
  assert.ok(sms.debugCode, 'requires an already-enabled dev SMS mock; never use an external SMS service')
  const login = await data('POST', `${auth}/consumer/login`, { phone, code: sms.debugCode }, 200, false)
  token = login.accessToken; refreshToken = login.refreshToken
  const session = await data('POST', '/chat/sessions', { title: '20261115 Agent 浏览器链路验收（合成）' }, 201)
  report.sessionId = session.sessionId
  assert.match(session.sessionId, /^[1-9][0-9]{0,18}$/)
  assert.equal(session.status, 'ACTIVE')
  const path = `/chat/sessions/${session.sessionId}`
  const page = await data('GET', '/chat/sessions?size=1')
  assert.equal(page.items[0].sessionId, session.sessionId)
  assert.equal((await data('GET', path)).sessionId, session.sessionId)
  const payload = { content: '这是前后端联调测试，请用一句话介绍店铺服务。', clientMessageId: crypto.randomUUID() }
  const started = performance.now()
  const stream = await call('POST', `${path}/messages`, payload)
  assert.match(stream.headers.get('content-type'), /^text\/event-stream/)
  let buffer = ''
  const decoder = new TextDecoder()
  for await (const chunk of stream.body) {
    buffer += decoder.decode(chunk, { stream: true })
    let delimiter
    while ((delimiter = /\r?\n\r?\n/.exec(buffer))) {
      const frame = buffer.slice(0, delimiter.index)
      buffer = buffer.slice(delimiter.index + delimiter[0].length)
      const event = /^event:\s*(.+)$/m.exec(frame)?.[1].trim()
      const raw = /^data:\s*(.+)$/m.exec(frame)?.[1]
      if (event && raw) {
        const value = JSON.parse(raw)
        report.events.push({ event, ms: Math.round(performance.now() - started),
          ...(event === 'error' ? { code: value.code, message: value.message } : {}) })
      }
    }
  }
  assert.equal(report.events[0]?.event, 'start')
  assert.equal(report.events.at(-1)?.event, 'done', 'real model did not complete; inspect redacted report')
  assert.ok(report.events.some(event => event.event === 'token'))
  const history = await data('GET', `${path}/messages?size=1`)
  assert.equal(history.items.length, 1)
  assert.equal(history.items[0].senderType, 'USER')
  assert.equal(history.hasMore, true)
  const next = await data('GET', `${path}/messages?size=1&cursor=${encodeURIComponent(history.nextCursor)}`)
  const assistant = next.items[0]
  assert.equal(assistant.senderType, 'ASSISTANT')
  assert.equal(assistant.status, 'COMPLETED')
  assert.ok(assistant.content)
  const retry = await call('POST', `${path}/messages`, payload)
  assert.match(retry.headers.get('content-type'), /^application\/json/)
  const replay = (await retry.json()).data
  assert.equal(replay.messageId, assistant.messageId)
  assert.equal(replay.status, 'COMPLETED')
  await call('POST', `${path}/messages`, { ...payload, content: '不同正文' }, 409)
  await call('GET', `${path}/messages?size=0`, undefined, 400)
  assert.equal((await data('POST', `${path}/archive`)).status, 'ARCHIVED')
  assert.equal((await data('POST', `${path}/archive`)).status, 'ARCHIVED')
  await call('POST', `${path}/messages`, { ...payload, clientMessageId: crypto.randomUUID() }, 409)
  assert.equal((await data('GET', `${path}/messages`)).items.length, 2)
  report.result = 'PASS'
} catch (error) {
  report.result = 'FAIL'; report.error = error.message
  process.exitCode = 1
} finally {
  if (token) await call('POST', '/auth/logout', { refreshToken }).catch(() => { report.logoutFailed = true })
  const output = resolve('../api/target/nginx-browser')
  await mkdir(output, { recursive: true })
  await writeFile(resolve(output, 'agent-dev-results.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report, null, 2))
}
