import { get } from './request'
import type { PageQuery, PageResult, ProductDetail, ProductSummary, Sku, StockStatus } from './types'

const DEFAULT_STORE_SLUG = import.meta.env.VITE_DEFAULT_MERCHANT_CODE || 'M_3C_DEMO'

/**
 * 店铺定位：后端契约确认「普通店铺主页 /store/{storeSlug} 由 URL 公开带定，
 * 无需消费者手动 merchantCode」。前端入口路由 /store/:slug 会把 slug 存入
 * localStorage，本函数每次请求时读取，保证 URL 换店后立即生效。
 */
function currentStoreSlug(): string {
  if (typeof localStorage !== 'undefined') {
    const fromUrl = localStorage.getItem('gmb.store_slug')
    if (fromUrl) return fromUrl
  }
  return DEFAULT_STORE_SLUG
}

export interface PublicProductQuery extends PageQuery {
  keyword?: string
  category?: string
  sort?: 'recommend' | 'price-asc' | 'price-desc'
}

/** 匿名公开商城：GET /v1/stores/{storeSlug}/products（URL 解析租户，不需要登录） */
export function listPublicProducts(query: PublicProductQuery = {}) {
  return get<PageResult<ProductSummary>>(`/v1/stores/${encodeURIComponent(currentStoreSlug())}/products`, query)
}

export function getPublicProduct(productId: string) {
  return get<ProductDetail>(`/v1/stores/${encodeURIComponent(currentStoreSlug())}/products/${productId}`)
}

/** 公开库存只返回状态枚举，不返回具体数量 */
export function getSkuAvailability(skuId: string) {
  return get<{ skuId: string; stockStatus: StockStatus }>(`/v1/skus/${skuId}/availability`)
}

export function getSku(skuId: string) {
  return get<Sku>(`/v1/skus/${skuId}`)
}

/** 登录后的商品管理视图（商户端） */
export function listProducts(query: { keyword?: string } & PageQuery = {}) {
  return get<PageResult<ProductSummary>>('/v1/products', query)
}

export function getProduct(productId: string) {
  return get<ProductDetail>(`/v1/products/${productId}`)
}
