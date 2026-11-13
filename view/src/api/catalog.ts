import { get, post, put } from './request'
import type {
  CategoryView,
  PageView,
  ProductView,
  PublicProductView,
  PublicSkuView,
  SkuView
} from './types'

const DEFAULT_STORE_SLUG = import.meta.env.VITE_DEFAULT_MERCHANT_CODE || 'M_3C_DEMO'

/**
 * 店铺定位：后端契约确认「普通店铺主页 /store/{storeSlug} 由 URL 公开带定，
 * 无需消费者手动 merchantCode」。前端入口路由 /store/:slug 会把 slug 存入
 * localStorage，本函数每次请求时读取，保证 URL 换店后立即生效。
 */
export function currentStoreSlug(): string {
  if (typeof localStorage !== 'undefined') {
    const fromUrl = localStorage.getItem('gmb.store_slug')
    if (fromUrl) return fromUrl
  }
  return DEFAULT_STORE_SLUG
}

/** 游标分页查询参数：product-v1 没有 page/pageSize，只有 cursor + size（默认 20） */
export interface CatalogQuery {
  categoryId?: string
  cursor?: string
  size?: number
}

/* ------------------------------ 公开商城（匿名，URL 定位租户） ------------------------------ */

/** GET /v1/stores/{storeSlug}/products —— 只返回 PUBLISHED 商品及其 ACTIVE SKU */
export function listPublicProducts(query: CatalogQuery = {}) {
  const { categoryId, cursor, size } = query
  return get<PageView<PublicProductView>>(
    `/v1/stores/${encodeURIComponent(currentStoreSlug())}/products`,
    { categoryId, cursor, size }
  )
}

export function getPublicProduct(productId: string) {
  return get<PublicProductView>(
    `/v1/stores/${encodeURIComponent(currentStoreSlug())}/products/${productId}`
  )
}

export function getPublicSku(skuId: string) {
  return get<PublicSkuView>(`/v1/stores/${encodeURIComponent(currentStoreSlug())}/skus/${skuId}`)
}

/* ------------------------------ 商户端（Bearer，按 Actor 的租户过滤） ------------------------------ */

/** GET /v1/products —— 员工看本商户全部状态（DRAFT/PUBLISHED/UNPUBLISHED/DISCONTINUED） */
export function listProducts(query: CatalogQuery = {}) {
  const { categoryId, cursor, size } = query
  return get<PageView<ProductView>>('/v1/products', { categoryId, cursor, size })
}

export function getProduct(productId: string) {
  return get<ProductView>(`/v1/products/${productId}`)
}

export function getSku(skuId: string) {
  return get<SkuView>(`/v1/skus/${skuId}`)
}

export function listCategories() {
  return get<CategoryView[]>('/v1/product-categories')
}

/* ------------------------------ 写操作（商品负责人维护，页面上只开放上下架与 SKU 改价） ------------------------------ */

export function publishProduct(productId: string) {
  return post<ProductView>(`/v1/products/${productId}/publish`)
}

export function unpublishProduct(productId: string) {
  return post<ProductView>(`/v1/products/${productId}/unpublish`)
}

export function updateProduct(
  productId: string,
  body: { categoryId?: number | string; name?: string; brand?: string; description?: string }
) {
  return put<ProductView>(`/v1/products/${productId}`, body)
}

export function updateSku(
  skuId: string,
  body: { title?: string; price?: number | string; attributes?: Record<string, string>; status?: string }
) {
  return put<SkuView>(`/v1/skus/${skuId}`, body)
}
