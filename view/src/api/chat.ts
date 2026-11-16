import { get, post, shouldMock, ApiError, newRequestId } from './request'
import { toChatBubble, toChatCard } from './adapters'
import type {
  ChatMessage,
  ChatMessageView,
  ChatPageView,
  ChatSession,
  ChatRunView,
  ChatSessionView,
  ChatSourceView,
  ChatToolCallView
} from './types'
import { getToken } from '@/utils/token'

/**
 * agent-conversation-v1 契约调用层。
 *
 * - 会话范围由登录主体（消费者 / 商户员工）决定，契约没有 scope 参数，不能传；
 * - 列表与历史均为游标分页（size + cursor，未知参数会被 400/10001 拒绝）；
 * - 发送消息必须带 clientMessageId 幂等键，新 run 返回 SSE（start → token → done/error），
 *   同 key 同正文重放返回 JSON run 标识；
 * - RAG 仅检索本商户 READY 资料，done 帧携带 sources（≤5）与 toolCalls（≤1），
 *   契约没有人工接管（:takeover / :resume-agent 不存在），相关入口已下线。
 *
 * 离线演示（VITE_USE_MOCK=true）时返回展示模型原样数据，与真实模式的转换结果同构。
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const SESSIONS_URL = '/v1/chat/sessions'

/** 伪随机请求号；request.ts 内部同源实现，这里复用保证帧与响应一致 */
const requestIdOf = newRequestId

export interface ListSessionsOptions {
  size?: number
  cursor?: string
  /** 仅离线 Mock 使用：区分消费者 / 商户侧演示数据；真实接口按登录主体返回 */
  audience?: 'consumer' | 'merchant'
}

export async function listSessions(options: ListSessionsOptions = {}): Promise<ChatSession[]> {
  const { size = 20, cursor, audience = 'consumer' } = options
  const params: Record<string, unknown> = { size }
  if (cursor) params.cursor = cursor
  if (shouldMock(SESSIONS_URL)) params.scope = audience

  if (shouldMock(SESSIONS_URL)) {
    return get<ChatSession[]>(SESSIONS_URL, params)
  }
  const page = await get<ChatPageView<ChatSessionView>>(SESSIONS_URL, params)
  return page.items.map(toChatCard)
}

/** title 可省略（契约：空白使用「新对话」），最多 200 字符 */
export async function createSession(title?: string): Promise<ChatSession> {
  const payload = title ? { title } : {}
  if (shouldMock(SESSIONS_URL)) return post<ChatSession>(SESSIONS_URL, payload)
  return toChatCard(await post<ChatSessionView>(SESSIONS_URL, payload))
}

export function getSession(sessionId: string) {
  return get<ChatSessionView>(`${SESSIONS_URL}/${sessionId}`)
}

/** 幂等归档；会话运行中会返回 409/13002 busy */
export function archiveSession(sessionId: string) {
  return post<ChatSessionView>(`${SESSIONS_URL}/${sessionId}/archive`)
}

/** 历史消息按 messageSeq 升序返回，hasMore 时用 nextCursor 取后续（更新的）页 */
export async function listMessages(sessionId: string, size = 50, cursor?: string): Promise<ChatMessage[]> {
  const params: Record<string, unknown> = { size }
  if (cursor) params.cursor = cursor
  if (shouldMock(SESSIONS_URL)) {
    return get<ChatMessage[]>(`${SESSIONS_URL}/${sessionId}/messages`)
  }
  const messages: ChatMessage[] = []
  for (;;) {
    const page = await get<ChatPageView<ChatMessageView>>(`${SESSIONS_URL}/${sessionId}/messages`, params)
    messages.push(...page.items.map(message => toChatBubble(message, sessionId)))
    if (!page.hasMore || !page.nextCursor) break
    params.cursor = page.nextCursor
  }
  return messages
}

export interface SendMessageHandlers {
  /** SSE start 帧：建立流式占位气泡 */
  onStart?: (message: ChatMessage) => void
  /** SSE token 帧：追加增量文本 */
  onDelta?: (message: ChatMessage, delta: string) => void
  /** 终态（COMPLETED / FAILED / TIMED_OUT / CANCELLED），或幂等重放的完整回复 */
  onDone?: (message: ChatMessage) => void
  onError?: (error: ApiError) => void
}

function newClientMessageId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `00000000-0000-4000-8000-${Date.now().toString().padStart(12, '0')}`
}

function assistantPlaceholder(messageId: string, sessionId: string): ChatMessage {
  return {
    id: messageId,
    sessionId,
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    streaming: true
  }
}

/**
 * 发送消息并消费流式回复。
 *
 * Mock 模式沿用一次性 JSON 回复（包含演示用引用与工具轨迹）；
 * 真实模式按契约处理 text/event-stream 与 application/json 两种响应。
 */
export async function sendMessage(
  sessionId: string,
  content: string,
  handlers: SendMessageHandlers = {},
  clientMessageId = newClientMessageId()
): Promise<void> {
  const url = `${SESSIONS_URL}/${sessionId}/messages`
  const payload = { content, clientMessageId }

  /* ---- 离线演示：Mock 直接给展示模型（一次性整段回复） ---- */
  if (shouldMock(url)) {
    const reply = await post<ChatMessage>(url, { content })
    handlers.onStart?.({ ...reply, streaming: true })
    handlers.onDelta?.(reply, reply.content)
    handlers.onDone?.(reply)
    return
  }

  /* ---- 真实模式：fetch + SSE（axios 不便处理事件流） ---- */
  let res: Response
  try {
    res = await fetch(`${BASE_URL}${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream, application/json',
        Authorization: `Bearer ${getToken()}`,
        'X-Request-Id': requestIdOf()
      },
      body: JSON.stringify(payload)
    })
  } catch {
    const err = new ApiError('网络异常，请确认后端已启动', 0, 0)
    handlers.onError?.(err)
    throw err
  }

  if (!res.ok) {
    let code = res.status
    let message = res.status === 502
      ? '网关无法连接后端服务（502）'
      : res.status === 504
        ? '后端服务响应超时（504）'
        : `发送失败（${res.status}）`
    const responseRequestId = res.headers.get('X-Request-Id') || undefined
    try {
      const body = (await res.json()) as { code?: number; message?: string }
      if (body?.message) message = body.message
      if (typeof body?.code === 'number') code = body.code
    } catch {
      /* 非 JSON 错误体，用兜底文案 */
    }
    if (responseRequestId) message += `（请求编号：${responseRequestId}）`
    const err = new ApiError(message, code, res.status, responseRequestId)
    handlers.onError?.(err)
    throw err
  }

  const contentType = res.headers.get('content-type') || ''

  /* 幂等重放只返回标识；按游标查找原助手，不能将它伪装成空白成功。 */
  if (contentType.includes('application/json')) {
    try {
      const body = (await res.json()) as { data?: ChatRunView }
      const run = body?.data
      if (!run?.messageId) throw new ApiError('服务未返回有效的聊天响应', 0, 200)
      const replay = (await listMessages(sessionId)).find(message => message.id === run.messageId)
      if (!replay) throw new ApiError('未找到原回答，请刷新历史', 0, 200)
      handlers.onStart?.(replay)
      handlers.onDone?.(replay)
    } catch (e) {
      const err = e instanceof ApiError ? e : new ApiError('原回答加载失败，请刷新历史', 0, 0)
      handlers.onError?.(err)
      throw err
    }
    return
  }

  if (!contentType.includes('text/event-stream')) {
    const err = new ApiError('服务未返回有效的聊天响应', 0, res.status)
    handlers.onError?.(err)
    throw err
  }

  /* SSE：start → token* → done | error（HTTP 已 200，终态可能在帧里） */
  const reader = res.body?.getReader()
  if (!reader) {
    const err = new ApiError('当前浏览器不支持流式响应', 0, 0)
    handlers.onError?.(err)
    throw err
  }

  const decoder = new TextDecoder()
  let buffer = ''
  let streaming: ChatMessage | null = null
  let terminal = false

  /** done 帧携带的 RAG 引用和工具调用摘要 */
  let donePayload: {
    sources?: ChatSourceView[]
    toolCalls?: ChatToolCallView[]
  } | null = null

  const finalize = (errorMessage?: string) => {
    if (!streaming) return

    const done: ChatMessage = { ...streaming, streaming: false }

    if (errorMessage) {
      done.content += `\n\n（${errorMessage}）`
    } else if (donePayload) {
      const citations = (donePayload.sources || []).map((s) => ({
        documentName: s.chunkNo > 1 ? `${s.title} · 第 ${s.chunkNo} 片` : s.title,
        snippet: s.excerpt
      }))
      const toolCalls = (donePayload.toolCalls || []).map((t) => ({
        tool: t.name,
        params: {},
        success: t.status === 'SUCCEEDED'
      }))

      if (citations.length) done.citations = citations
      if (toolCalls.length) done.toolCalls = toolCalls
    }

    streaming = null
    donePayload = null
    handlers.onDone?.(done)
  }

  const handleFrame = (frame: string) => {
    let event = 'message'
    let data = ''
    for (const line of frame.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5).trimStart()
    }
    if (!data) return
    let parsed: Record<string, unknown>
    try {
      parsed = JSON.parse(data) as Record<string, unknown>
    } catch {
      return
    }

    if (event === 'start') {
      const messageId = String(parsed.messageId ?? '')
      streaming = assistantPlaceholder(messageId, sessionId)
      handlers.onStart?.(streaming)
    } else if (event === 'token') {
      const delta = String(parsed.delta ?? '')
      if (streaming && delta) {
        streaming = { ...streaming, content: streaming.content + delta }
        handlers.onDelta?.(streaming, delta)
      }
    } else if (event === 'done') {
      terminal = true
      donePayload = {
        sources: Array.isArray(parsed.sources)
          ? (parsed.sources as unknown as ChatSourceView[])
          : undefined,
        toolCalls: Array.isArray(parsed.toolCalls)
          ? (parsed.toolCalls as unknown as ChatToolCallView[])
          : undefined
      }
      finalize()
    } else if (event === 'error') {
      terminal = true

      const code = parsed.code ?? parsed.errorCode
      const message = String(parsed.message || '回答生成失败，请重试')
      const errorMessage = typeof code === 'string' && code ? `${message}（${code}）` : message

      finalize(errorMessage)

      throw new ApiError(
        errorMessage,
        typeof code === 'number' ? code : 0,
        200,
        String(parsed.requestId ?? '')
      )
    }
  }

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let boundary: RegExpExecArray | null
      while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
        const frame = buffer.slice(0, boundary.index)
        buffer = buffer.slice(boundary.index + boundary[0].length)
        if (frame.trim()) handleFrame(frame)
        if (terminal) break
      }
      if (terminal) break
    }
    buffer += decoder.decode()
    if (!terminal) throw new ApiError('流式响应中断，请刷新历史确认结果', 0, 0)
  } catch (e) {
    const err =
      e instanceof ApiError ? e : new ApiError('流式响应中断，请重新发送', 0, 0)
    finalize(err.message)
    handlers.onError?.(err)
    throw err
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
