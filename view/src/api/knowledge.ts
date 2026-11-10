import { get, post } from './request'
import type { DocumentTask, KnowledgeBase, MerchantOverview } from './types'

export function listKnowledgeBases() {
  return get<KnowledgeBase[]>('/v1/knowledge-bases')
}

export function listDocumentTasks() {
  return get<DocumentTask[]>('/v1/document-tasks')
}

export function getDocumentTask(taskId: string) {
  return get<DocumentTask>(`/v1/document-tasks/${taskId}`)
}

/** 人工重投：命令式接口，用冒号风格 */
export function retryDocumentTask(taskId: string) {
  return post<DocumentTask>(`/v1/document-tasks/${taskId}:retry`)
}

/**
 * 工作台指标。
 * 设计手稿的接口清单尚未定义该资源，属于前端先行占位，
 * 需在 OpenSpec change 的 design 中评审后再由后端补齐（/v1/merchant/overview）。
 */
export function getMerchantOverview() {
  return get<MerchantOverview>('/v1/merchant/overview')
}
