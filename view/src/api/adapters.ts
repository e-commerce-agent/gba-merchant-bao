import type {
  ChatMessage,
  ChatMessageView,
  ChatSession,
  ChatSessionView,
  ProductDetail,
  ProductSummary,
  ProductThumbKey,
  PublicProductView,
  PublicSkuView,
  Sku,
  SkuView,
  StockStatus
} from './types'
import { thumbOf } from '@/utils/thumb'

/**
 * 契约类型 → 展示模型。
 *
 * product-v1 的商品视图只有名称、品牌、描述、属性与 SKU 列表：价格挂在 SKU 上，
 * 库存状态只有公开接口的 inStock 布尔值。卡片要展示「价格区间 / SKU 数 / 库存状态」，
 * 所以统一在这一层聚合，页面与组件不直接依赖契约结构。
 */

/** 后端 price 是字符串（BigDecimal.toPlainString），统一转成数字后展示 */
export function toNumber(price: string | number | undefined): number {
  const value = typeof price === 'number' ? price : Number(price ?? 0)
  return Number.isFinite(value) ? value : 0
}

export function priceRangeOf(skus: Array<{ price: string | number }>): { min: number; max: number } {
  const prices = skus.map((s) => toNumber(s.price)).filter((n) => n > 0)
  if (!prices.length) return { min: 0, max: 0 }
  return { min: Math.min(...prices), max: Math.max(...prices) }
}

/**
 * 公开接口只给 inStock（是否可售），拿不到数量，因此不区分「仅剩少量」：
 * 有任一 SKU 可售即有货，全部缺货即缺货。LOW_STOCK 只在商户端用库存数量判断。
 */
export function publicStockOf(skus: PublicSkuView[]): StockStatus {
  if (!skus.length) return 'OUT_OF_STOCK'
  return skus.some((s) => s.inStock) ? 'IN_STOCK' : 'OUT_OF_STOCK'
}

export function toPublicSku(sku: PublicSkuView): Sku {
  return {
    id: sku.skuId,
    productId: '',
    skuCode: sku.skuCode,
    name: sku.title,
    attributes: Object.entries(sku.attributes ?? {}).map(([name, value]) => ({ name, value })),
    price: toNumber(sku.price),
    currency: 'CNY',
    stockStatus: sku.inStock ? 'IN_STOCK' : 'OUT_OF_STOCK'
  }
}

export function toStaffSku(sku: SkuView): Sku {
  return {
    id: sku.skuId,
    productId: sku.productId,
    skuCode: sku.skuCode,
    name: sku.title,
    attributes: Object.entries(sku.attributes ?? {}).map(([name, value]) => ({ name, value })),
    price: toNumber(sku.price),
    currency: 'CNY',
    stockStatus: sku.status === 'ACTIVE' ? 'IN_STOCK' : 'OUT_OF_STOCK'
  }
}

/** 商品属性（key-value）在详情页作为「规格参数」展示 */
function specsOf(attributes: Record<string, string>): Array<{ label: string; value: string }> {
  return Object.entries(attributes ?? {}).map(([label, value]) => ({ label, value }))
}

function tagsOf(attributes: Record<string, string>): string[] {
  return Object.entries(attributes ?? {})
    .slice(0, 3)
    .map(([name, value]) => `${name}：${value}`)
}

export function toCardModel(product: PublicProductView): ProductSummary {
  const { min, max } = priceRangeOf(product.skus)
  const thumb: ProductThumbKey = thumbOf(product.name)
  return {
    id: product.productId,
    name: product.name,
    subtitle: product.brand || product.productCode,
    categoryName: '',
    brand: product.brand,
    model: product.productCode,
    status: 'ON_SALE',
    thumb,
    minPrice: min,
    maxPrice: max,
    currency: 'CNY',
    skuCount: product.skus.length,
    tags: tagsOf(product.attributes),
    stockStatus: publicStockOf(product.skus)
  }
}

export function toDetailModel(product: PublicProductView): ProductDetail {
  return {
    ...toCardModel(product),
    description: product.description || product.name,
    /** 契约没有卖点/兼容性字段，留空后由页面隐藏对应区块 */
    sellingPoints: [],
    compatibility: [],
    specs: specsOf(product.attributes),
    skus: product.skus.map(toPublicSku),
    afterSale: '7 天无理由退换，18 个月内非人为损坏只换不修；具体以商品页说明与订单约定为准。'
  }
}

/* ------------------------- Agent 会话（agent-conversation-v1） ------------------------- */

/**
 * 会话卡片。契约只有 ACTIVE/ARCHIVED 两种状态，也没有 lastMessage / messageCount /
 * consumerName / 置信度（citation、人工接管均为未来扩展），对应展示位留空由页面降级显示。
 */
export function toChatCard(session: ChatSessionView): ChatSession {
  return {
    id: session.sessionId,
    title: session.title || '新的咨询',
    status: session.status === 'ARCHIVED' ? 'CLOSED' : 'AGENT',
    lastMessage: '',
    updatedAt: session.updatedAt,
    messageCount: 0,
    confidence: 1,
    unread: 0
  }
}

const CHAT_FAILED_TEXT: Record<string, string> = {
  CANCELLED: '本次回答已取消',
  TIMED_OUT: '回答超时，请重试',
  FAILED: '回答生成失败，请重试'
}

/** 消息气泡。契约 senderType 只有 USER/ASSISTANT（人工客服身份为未来扩展） */
export function toChatBubble(message: ChatMessageView): ChatMessage {
  const failedText = CHAT_FAILED_TEXT[message.status]
  return {
    id: message.messageId,
    sessionId: message.runId,
    role: message.senderType === 'USER' ? 'user' : 'assistant',
    content: failedText ? `${message.content}\n\n（${failedText}）` : message.content,
    createdAt: message.createdAt,
    streaming: message.status === 'STARTED' || message.status === 'STREAMING'
  }
}
