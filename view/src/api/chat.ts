import { get, post } from './request'
import type { ChatMessage, ChatSession } from './types'

/** 统一会话资源，返回范围由当前主体（消费者 / 商户员工）决定 */
export function listSessions(scope: 'consumer' | 'merchant') {
  return get<ChatSession[]>('/v1/chat/sessions', { scope })
}

export function createSession(title: string) {
  return post<ChatSession>('/v1/chat/sessions', { title })
}

export function listMessages(sessionId: string) {
  return get<ChatMessage[]>(`/v1/chat/sessions/${sessionId}/messages`)
}

/**
 * 发送消息。
 * 消费者走 SSE：POST /v1/chat/sessions/{id}/messages:stream（流式，待后端交付）
 * 人工客服回复走普通 JSON：POST /v1/chat/sessions/{id}/messages
 */
export function sendMessage(sessionId: string, content: string) {
  return post<ChatMessage>(`/v1/chat/sessions/${sessionId}/messages`, { content })
}

/** 客服接管会话 */
export function takeoverSession(sessionId: string) {
  return post<{ sessionId: string; status: string }>(`/v1/chat/sessions/${sessionId}:takeover`)
}

/** 交还 Agent */
export function resumeAgent(sessionId: string) {
  return post<{ sessionId: string; status: string }>(
    `/v1/chat/sessions/${sessionId}:resume-agent`
  )
}
