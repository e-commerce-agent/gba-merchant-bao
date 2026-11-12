import { get, post } from './request'
import type { InventoryPage, InventoryVO } from './types'

/**
 * 库存维护（inventory-v1）。全部接口要求 MERCHANT_STAFF：
 * 商户与操作人只来自可信 Actor，消费者调用返回 400/13006。
 * 写入接口用 idempotentKey 做幂等，成功重放不会重复记账。
 */

/** GET /v1/inventory/page —— pageNo 从 1 开始，pageSize 默认 20 */
export function listInventory(pageNo = 1, pageSize = 20) {
  return get<InventoryPage>('/v1/inventory/page', { pageNo, pageSize })
}

export function getInventory(skuId: string) {
  return get<InventoryVO>(`/v1/inventory/${skuId}`)
}

/**
 * 盘点调整：adjustQty 可正可负，首次正向调整会从零初始化库存行。
 * 负数调整后不得低于零（否则 409/13007）。
 */
export function adjustInventory(input: {
  skuId: string
  adjustQty: number
  remark?: string
  idempotentKey?: string
}) {
  return post<void>('/v1/inventory/adjust', {
    idempotentKey: input.idempotentKey || newIdempotentKey(),
    skuId: input.skuId,
    adjustQty: input.adjustQty,
    remark: input.remark
  })
}

/** 幂等键：同一次提交重试必须复用，重新填写表单时重新生成 */
export function newIdempotentKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `adj-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}
