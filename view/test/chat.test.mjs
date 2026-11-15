import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { build } from 'esbuild'
import { resolve } from 'node:path'

// Exercise the actual API adapter and SSE reader; replace only HTTP/auth boundaries.
globalThis.chatTestHttp = {}
const compiled = await build({ entryPoints: ['src/api/chat.ts'], bundle: true, write: false,
  format: 'esm', platform: 'node', alias: { '@': resolve('src') },
  define: { 'import.meta.env': '{}' }, plugins: [{ name: 'http-boundary', setup(build) {
    build.onResolve({ filter: /^\.\/request$/ }, () => ({ path: 'request', namespace: 'test' }))
    build.onLoad({ filter: /.*/, namespace: 'test' }, () => ({ contents: `
      export const get = (...args) => globalThis.chatTestHttp.get(...args);
      export const post = (...args) => globalThis.chatTestHttp.post(...args);
      export const shouldMock = () => Boolean(globalThis.chatTestHttp.mock);
      export const newRequestId = () => 'chat-test';
      export class ApiError extends Error { constructor(message,code,status=0,requestId) {
        super(message);Object.assign(this,{code,status,requestId}); } }
    ` }))
    build.onResolve({ filter: /utils\/token$/ }, () => ({ path: 'token', namespace: 'token' }))
    build.onLoad({ filter: /.*/, namespace: 'token' }, () => ({ contents: `export const getToken=()=> 'test-only';` }))
  } }] })
const chat = await import(`data:text/javascript;base64,${Buffer.from(compiled.outputFiles[0].text).toString('base64')}`)
const sessionId = '300000000000000001', messageId = '300000000000000002'
const source = { documentId: '300000000000000003', chunkId: '300000000000000004', title: '售后政策', chunkNo: 1, excerpt: '支持保修' }
const record = { messageId, runId: '300000000000000005', messageSeq: 2, senderType: 'ASSISTANT',
  content: '你好', status: 'COMPLETED', createdAt: '2026-11-15T08:00:00Z', sources: [source],
  toolCalls: [{ name: 'lookup_order', status: 'SUCCEEDED' }] }
const frame = (event, data) => `event: ${event}\r\ndata: ${JSON.stringify(data)}\r\n\r\n`
function stream(text) {
  const bytes = new TextEncoder().encode(text)
  return new Response(new ReadableStream({ start(controller) {
    // One-byte chunks split UTF-8 characters and CRLF frame delimiters.
    for (let i=0;i<bytes.length;i++) controller.enqueue(bytes.slice(i,i+1))
    controller.close()
  } }), { headers: { 'Content-Type': 'text/event-stream' } })
}
beforeEach(() => { globalThis.chatTestHttp = { get: async () => ({ items: [record], hasMore: false, nextCursor: null }) } })

test('createSession converts contract sessionId and ACTIVE into the UI session', async () => {
  globalThis.chatTestHttp.post = async () => ({ sessionId, title: '联调', status: 'ACTIVE', updatedAt: record.createdAt })
  const created = await chat.createSession('联调')
  assert.equal(created.id, sessionId)
  assert.equal(created.status, 'AGENT')
})
test('history keeps the actual session ID and basic message content', async () => {
  const [message] = await chat.listMessages(sessionId)
  assert.equal(message.sessionId, sessionId)
  assert.equal(message.content, record.content)
  assert.equal(message.role, 'assistant')
})
test('SSE handles fragmented CRLF/UTF-8 with optional metadata present', async () => {
  globalThis.fetch = async () => stream(frame('start', { messageId, sessionId, runId: record.runId }) +
    frame('token', { delta: '你好' }) + frame('done', { status: 'COMPLETED', sources: [source], toolCalls: record.toolCalls }))
  const finished = [], deltas = []
  await chat.sendMessage(sessionId, '测试', { onDelta: (_, delta) => deltas.push(delta), onDone: m => finished.push(m) })
  assert.deepEqual(deltas, ['你好'])
  assert.equal(finished.length, 1)
  assert.equal(finished[0].content, '你好')
  assert.equal(finished[0].sessionId, sessionId)
})
test('EOF without done/error reports an interruption instead of success', async () => {
  globalThis.fetch = async () => stream(frame('start', { messageId, sessionId, runId: record.runId }) + frame('token', { delta: '部分' }))
  const errors = []
  await assert.rejects(chat.sendMessage(sessionId, '测试', { onError: e => errors.push(e) }))
  assert.equal(errors.length, 1)
})
test('SSE error uses the contract code and message, then reports failure', async () => {
  globalThis.fetch = async () => stream(frame('start', { messageId, sessionId, runId: record.runId }) +
    frame('error', { code: 13008, message: '模型暂不可用', requestId: 'failed-run', status: 'FAILED' }))
  const errors = []
  await assert.rejects(chat.sendMessage(sessionId, '测试', { onError: e => errors.push(e) }))
  assert.equal(errors.length, 1)
  assert.equal(errors[0].code, 13008)
  assert.equal(errors[0].message, '模型暂不可用')
})
test('idempotent JSON replay loads the existing assistant from paged history', async () => {
  globalThis.fetch = async () => Response.json({ code: 0, data: { sessionId, messageId, runId: record.runId, status: 'COMPLETED' } })
  const finished = []
  const cursors = []
  globalThis.chatTestHttp.get = async (_, params) => {
    cursors.push(params.cursor)
    return params.cursor ? { items: [record], hasMore: false, nextCursor: null }
      : { items: [], hasMore: true, nextCursor: 'page-two' }
  }
  await chat.sendMessage(sessionId, '测试', { onDone: m => finished.push(m) }, 'stable-client-key')
  assert.equal(finished[0].content, record.content)
  assert.equal(finished[0].streaming, false)
  assert.deepEqual(cursors, [undefined, 'page-two'])
})
test('offline Mock creation and sending keep the existing display model', async () => {
  const mock = { id: sessionId, status: 'AGENT', title: '离线会话', content: '演示回复' }
  globalThis.chatTestHttp.mock = true
  globalThis.chatTestHttp.post = async () => mock
  assert.deepEqual(await chat.createSession(), mock)
  const finished = []
  await chat.sendMessage(sessionId, '测试', { onDone: message => finished.push(message) })
  assert.deepEqual(finished, [mock])
})
test('invalid idempotency response reports failure to the page', async () => {
  globalThis.fetch = async () => Response.json({ code: 0, data: {} })
  const errors = []
  await assert.rejects(chat.sendMessage(sessionId, '测试', { onError: error => errors.push(error) }))
  assert.equal(errors.length, 1)
})
