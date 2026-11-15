import { get, post, shouldMock, upload } from './request'
import type { KnowledgeDocumentView, KnowledgePageView, MerchantOverview } from './types'

/**
 * knowledge-v1 契约调用层：仅 MERCHANT_STAFF 可访问本商户资料。
 *
 * - 同步导入：POST multipart 仅 title + file 两个字段，无任务/重试接口；
 * - 列表按 createdAt/id 倒序游标分页（size + cursor），nextCursor 原样透传；
 * - RAG 关闭时上传 503（13501 RAG 禁用 / 13502 向量依赖失败），列表仍可读；
 * - 前端预校验：UTF-8 TXT/MD、≤1 MiB（契约 413/415 之外的体验兜底）。
 */

const URL = '/v1/knowledge/documents'

export function listKnowledgeDocuments(size = 20, cursor?: string) {
  const params: Record<string, unknown> = { size }
  if (cursor) params.cursor = cursor
  return get<KnowledgePageView>(URL, params)
}

export interface UploadKnowledgeInput {
  title: string
  /** UTF-8 编码的 .txt / .md 文件 */
  file: File
}

export function uploadKnowledgeDocument({ title, file }: UploadKnowledgeInput) {
  if (shouldMock(URL)) {
    // Mock：没有真实文件流，传文件名供展示
    return post<KnowledgeDocumentView>(URL, { title, fileName: file.name })
  }
  const form = new FormData()
  form.append('title', title)
  form.append('file', file)
  return upload<KnowledgeDocumentView>(URL, form)
}

/**
 * 工作台指标。设计手稿的接口清单尚未定义该资源，属于前端先行占位，
 * 需在 OpenSpec change 的 design 中评审后再由后端补齐（/v1/merchant/overview）。
 */
export function getMerchantOverview() {
  return get<MerchantOverview>('/v1/merchant/overview')
}
