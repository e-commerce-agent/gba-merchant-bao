/**
 * 与后端 /api/v1 契约对齐的类型定义。
 * 后端 OpenAPI 导出后，以 /v3/api-docs.yaml 为准核对字段，不在此处猜业务码。
 */

/** 统一响应体：HTTP 状态表达协议结果，body.code 表达业务结果（0 = 成功） */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

/** 分页结构（后端未最终冻结，先按最常见形态约定） */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface PageQuery {
  page?: number
  pageSize?: number
}

/* ------------------------------ 认证 ------------------------------ */

/** 认证主体类型：消费者 / 商户员工（首期商户管理员与客服合并） */
export type SubjectType = 'CONSUMER' | 'MERCHANT_STAFF'

export interface AuthUser {
  subjectId: string
  subjectType: SubjectType
  displayName: string
  phone?: string
  merchantId?: string
  merchantCode?: string
  merchantName?: string
  /** 权限点，首期不做 RBAC，先透传后端返回值 */
  permissions?: string[]
}

/* ---------- 以下为 auth-v1.openapi.yaml 契约类型（字段名与契约一一对应） ---------- */

/** 契约 SmsResult：发送短信验证码的返回 */
export interface SmsResult {
  /** 验证码有效期（秒），契约约定 300 秒 */
  expiresIn: number
  /** 可重发倒计时（秒），由限流窗口决定；429 时另带 Retry-After 响应头 */
  retryAfter: number
  /** 仅 dev mock 且显式开启时返回，联调用来自动回填 */
  debugCode?: string
}

/** 契约 PrincipalView：/auth/me 与登录返回的 principal */
export interface PrincipalView {
  accountId: string
  merchantId?: string
  merchantCode?: string
  role: SubjectType
  displayName: string
  phoneMasked?: string
}

/** 契约 Tokens：登录 / 续期统一返回 */
export interface Tokens {
  accessToken: string
  refreshToken: string
  tokenType: string
  /** 秒 */
  expiresIn: number
  /** UTC date-time，优先用它判断过期 */
  expiresAt: string
  refreshExpiresAt: string
  principal: PrincipalView
}

/* ------------------------------ 商品 ------------------------------ */

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'

export interface ProductSummary {
  id: string
  merchantId: string
  name: string
  subtitle?: string
  categoryName: string
  brand?: string
  model?: string
  status: 'ON_SALE' | 'OFF_SHELF'
  /** 展示用主图标识，Mock 阶段由前端绘制插画 */
  thumb: ProductThumbKey
  minPrice: number
  maxPrice: number
  currency: string
  skuCount: number
  tags: string[]
  stockStatus: StockStatus
}

export type ProductThumbKey =
  | 'charger'
  | 'cable'
  | 'powerbank'
  | 'earphone'
  | 'car'
  | 'wireless'
  | 'dock'

export interface SkuAttribute {
  name: string
  value: string
}

export interface Sku {
  id: string
  productId: string
  skuCode: string
  name: string
  attributes: SkuAttribute[]
  price: number
  currency: string
  stockStatus: StockStatus
  /** 仅商户端可见；公开端只暴露状态 */
  stockQty?: number
}

export interface ProductDetail extends ProductSummary {
  description: string
  sellingPoints: string[]
  /** 兼容性：机型 / 接口类型 / 快充协议 */
  compatibility: string[]
  specs: Array<{ label: string; value: string }>
  skus: Sku[]
  afterSale: string
}

/* ------------------------------ 订单 / 物流 ------------------------------ */

/** 与 V6 迁移的 ck_trade_order_status 一致：PENDING_SHIPMENT = 已付款待发货 */
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PENDING_SHIPMENT'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'

export type PayStatus = 'UNPAID' | 'PAID' | 'REFUNDING' | 'REFUNDED'

/** GET /api/v1/orders 的 records 元素（order-v1 契约 Summary，金额为元、时间为 UTC） */
export interface OrderSummary {
  orderId: string
  orderNo: string
  status: OrderStatus
  payStatus: PayStatus
  totalAmount: number
  paidAmount: number
  currency: string
  placedAt: string
}

/** GET /api/v1/orders/{orderId} 的 items 元素：下单时冻结的名称/属性/价格快照 */
export interface OrderItemView {
  itemId: string
  productId: string
  skuId: string
  productName: string
  /** 下单时的 SKU 属性快照，形如「颜色:深空灰;接口:USB-C」 */
  skuAttributes: string
  unitPrice: number
  quantity: number
  amount: number
}

export interface OrderDetail extends OrderSummary {
  items: OrderItemView[]
}

/** 订单分页用后端字段名 records/current/size，与通用 PageResult 不同 */
export interface OrderPage {
  records: OrderSummary[]
  total: number
  current: number
  size: number
}

/** 物流状态闭集；无运单记录时后端合成 NOT_SHIPPED（空轨迹） */
export type ShipmentStatus = 'NOT_SHIPPED' | 'IN_TRANSIT' | 'DELIVERED'

export interface ShipmentTrack {
  eventTime: string
  status: string
  description: string
  location?: string
}

/** GET /api/v1/orders/{orderId}/shipment（shipment-v1 契约 ShipmentView） */
export interface ShipmentView {
  orderId: string
  orderNo?: string
  status: ShipmentStatus
  carrierName?: string
  carrierCode?: string
  trackingNo?: string
  estimatedDeliveryAt?: string
  deliveredAt?: string
  tracks: ShipmentTrack[]
}

/* ------------------------------ 会话 / 消息 ------------------------------ */

export type SessionStatus = 'AGENT' | 'HUMAN' | 'CLOSED'

export type MessageRole = 'user' | 'assistant' | 'human' | 'system'

export interface Citation {
  documentName: string
  snippet: string
  score?: number
}

export interface ToolCallTrace {
  tool: string
  /** 只读 Tool，入参已脱敏 */
  params: Record<string, unknown>
  latencyMs?: number
  success: boolean
}

export interface ChatMessage {
  id: string
  sessionId: string
  role: MessageRole
  content: string
  createdAt: string
  /** 知识库来源引用，Agent 回答才有 */
  citations?: Citation[]
  /** Function Calling 调用轨迹，用于商户端排查 */
  toolCalls?: ToolCallTrace[]
  /** 置信度，低置信度触发人工接管提示 */
  confidence?: number
  /** 流式输出中 */
  streaming?: boolean
}

export interface ChatSession {
  id: string
  title: string
  status: SessionStatus
  lastMessage: string
  updatedAt: string
  messageCount: number
  consumerName?: string
  confidence?: number
  unread?: number
}

/* ------------------------------ 知识库 / 任务 ------------------------------ */

export type DocumentTaskStatus =
  | 'QUEUED'
  | 'PARSING'
  | 'CHUNKING'
  | 'EMBEDDING'
  | 'SUCCEEDED'
  | 'FAILED'

export interface DocumentTask {
  id: string
  fileName: string
  fileSize: number
  status: DocumentTaskStatus
  progress: number
  chunkCount: number
  createdAt: string
  errorMessage?: string
}

export interface KnowledgeBase {
  id: string
  name: string
  description: string
  documentCount: number
  chunkCount: number
  updatedAt: string
}

/* ------------------------------ 工作台指标 ------------------------------ */

export interface MerchantOverview {
  sessionTotal: number
  sessionDelta: number
  autoResolvedRate: number
  takeoverCount: number
  avgFirstResponseSec: number
  pendingTasks: number
  lowConfidenceSessions: number
  trend: Array<{ date: string; sessions: number; takeovers: number }>
}
