import type {
  AuthUser,
  ChatMessage,
  ChatSession,
  DocumentTask,
  PageResult,
  PrincipalView,
  ProductDetail,
  ProductSummary,
  ShipmentView,
  SmsResult,
  Tokens
} from '@/api/types'
import {
  consumerMessages,
  consumerSessions,
  documentTasks,
  knowledgeBases,
  merchantMessages,
  merchantOverview,
  merchantSessions,
  MERCHANT,
  orderItems,
  orders,
  productDetails,
  products,
  shipments,
  sku
} from './db'

/**
 * 本地 Mock 分发器。
 * 只在 VITE_USE_MOCK=true 时生效，不依赖 Service Worker，切换真实接口时整份文件可删除。
 */

interface MockConfig {
  url: string
  method: string
  data?: unknown
  params?: Record<string, unknown>
}

const LATENCY = 220

const delay = (ms = LATENCY) => new Promise((resolve) => setTimeout(resolve, ms))

class MockError extends Error {
  code: number
  status: number
  constructor(message: string, code = 40000, status = 400) {
    super(message)
    this.code = code
    this.status = status
  }
}

/** 会话消息的运行期副本，让前端交互有真实反馈 */
const sessionStore = new Map<string, ChatMessage[]>([
  ['CS1001', [...consumerMessages]],
  ['CS2001', [...merchantMessages]],
  ['CS2002', [...consumerMessages.slice(0, 2)]],
  ['CS2003', [...consumerMessages.slice(2)]],
  ['CS2004', []]
])

const sessionStoreMeta = new Map<string, ChatSession>([
  ...consumerSessions.map((s) => [s.id, { ...s }] as [string, ChatSession]),
  ...merchantSessions.map((s) => [s.id, { ...s }] as [string, ChatSession])
])

function uid(prefix: string): string {
  return `${prefix}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
}

/** 没有单独维护明细的商品，按 SPU 信息合成一份可用的 SKU 明细，避免表格展开空白 */
function buildFallbackDetail(id: string): ProductDetail | undefined {
  const base = products.find((p) => p.id === id)
  if (!base) return undefined
  const statuses: ProductDetail['skus'] = [
    sku(base.id + '01', base.id, `${base.name} · 标准版`, [{ name: '版本', value: '标准版' }], base.minPrice, 'IN_STOCK', 36),
    sku(base.id + '02', base.id, `${base.name} · 套装版`, [{ name: '版本', value: '套装版' }], base.maxPrice, 'LOW_STOCK', 8)
  ]
  return {
    ...base,
    description: base.subtitle || base.name,
    sellingPoints: base.tags,
    compatibility: ['详见产品说明书', '支持主流手机与平板设备'],
    specs: [
      { label: '品牌', value: base.brand || '—' },
      { label: '型号', value: base.model || '—' },
      { label: '类目', value: base.categoryName }
    ],
    skus: statuses,
    afterSale: '7 天无理由退换，12 个月保修。'
  }
}

const consumerUser: AuthUser = {
  subjectId: 'U2001',
  subjectType: 'CONSUMER',
  displayName: '李小姐',
  phone: '13800138000',
  permissions: ['chat:read', 'chat:write', 'order:read']
}

const staffUser: AuthUser = {
  subjectId: 'U1001',
  subjectType: 'MERCHANT_STAFF',
  displayName: '吴小秀',
  phone: '13900139000',
  merchantId: MERCHANT.id,
  merchantCode: MERCHANT.code,
  merchantName: MERCHANT.name,
  permissions: [
    'product:read',
    'product:write',
    'inventory:read',
    'chat:read',
    'chat:write',
    'chat:takeover',
    'knowledge:read',
    'knowledge:write',
    'task:read'
  ]
}

function principalOf(user: AuthUser): PrincipalView {
  return {
    accountId: user.subjectId,
    merchantId: user.merchantId,
    merchantCode: user.merchantCode,
    role: user.subjectType,
    displayName: user.displayName,
    phoneMasked: user.phone ? `${user.phone.slice(0, 3)}****${user.phone.slice(-4)}` : undefined
  }
}

/** 按契约 Tokens 结构造假令牌，字段与 auth-v1.openapi.yaml 一致 */
function buildTokens(user: AuthUser): Tokens {
  const now = Date.now()
  return {
    accessToken: `mock.${user.subjectType.toLowerCase()}.${now}`,
    refreshToken: `mock.refresh.${user.subjectType.toLowerCase()}.${now}`,
    tokenType: 'Bearer',
    expiresIn: 7200,
    expiresAt: new Date(now + 7200_000).toISOString(),
    refreshExpiresAt: new Date(now + 30 * 86400_000).toISOString(),
    principal: principalOf(user)
  }
}

/* ------------------------------ 路由匹配 ------------------------------ */

function matchPath(pattern: string, path: string): Record<string, string> | null {
  const pSeg = pattern.split('/').filter(Boolean)
  const uSeg = path.split('/').filter(Boolean)
  if (pSeg.length !== uSeg.length) return null
  const params: Record<string, string> = {}
  for (let i = 0; i < pSeg.length; i += 1) {
    const p = pSeg[i]
    if (p.startsWith(':')) {
      params[p.slice(1)] = decodeURIComponent(uSeg[i])
    } else if (p !== uSeg[i]) {
      return null
    }
  }
  return params
}

/** 把 URL 里的 :action 动作段（如 sessions/{id}:takeover）拆成 path + action */
function normalize(url: string): { path: string; action: string } {
  const [rawPath, rawQuery] = url.split('?')
  const segments = rawPath.split('/')
  const last = segments[segments.length - 1]
  if (last.includes(':')) {
    const [tail, action] = last.split(':')
    segments[segments.length - 1] = tail
    return { path: segments.join('/') + (rawQuery ? `?${rawQuery}` : ''), action }
  }
  return { path: rawPath, action: '' }
}

function paginate<T>(list: T[], page = 1, pageSize = 12): PageResult<T> {
  const start = (page - 1) * pageSize
  return {
    items: list.slice(start, start + pageSize),
    total: list.length,
    page,
    pageSize
  }
}

/* ------------------------------ 假 Agent 回复 ------------------------------ */

const REPLY_LIBRARY: Array<{ match: RegExp; reply: string; citations?: ChatMessage['citations'] }> = [
  {
    match: /库存|有货|缺货|还有/,
    reply:
      '我查了一下实时库存：白色国标款有货，黑色国标款只剩少量，黑色英标款暂时缺货。\n\n公开页只展示「有货 / 少量 / 缺货」三种状态，具体数量属于商户内部数据，需要的话可以帮你留货。',
    citations: [
      { documentName: '公开库存展示规范.md', snippet: '公开端仅返回库存状态枚举，不返回具体数量。', score: 0.84 }
    ]
  },
  {
    match: /物流|快递|到哪|发货/,
    reply:
      '您的订单 GMB202610280001 已由顺丰揽收，11-01 从广州天河仓发出，11-02 到达广州白云集散中心，预计 11-04 送达。\n\n需要我帮您看一下详细的物流轨迹吗？',
    citations: [{ documentName: '承运商时效说明.md', snippet: '广东省内顺丰次日达，跨省 2-3 天。', score: 0.79 }]
  },
  {
    match: /售后|退|换|保修|坏/,
    reply:
      '售后政策是这样的：7 天内支持无理由退换（商品需保持完好），18 个月内非人为损坏只换不修，人为损坏不在保修范围内。\n\n如果是在保修期内出现质量问题，运费由我们承担。您可以把订单号发我，我帮您登记。',
    citations: [
      {
        documentName: '售后政策 2026 版.docx',
        snippet: '18 个月内非人为损坏只换不修；人为损坏（进水、摔裂）不在保修范围。',
        score: 0.93
      }
    ]
  },
  {
    match: /价格|多少钱|贵|优惠/,
    reply: '这款目前售价 ¥129（国标）/ ¥139（英标港版），暂时没有额外折扣。批量采购 20 件以上可以走批发价，需要的话我帮您转人工报价。'
  },
  {
    match: /兼容|支持|能用|适配|Mac|mac|笔记本/,
    reply:
      '这款支持 PD3.0 协议，单口最高 65W，可以给 MacBook Air、MateBook 这类支持 PD 的轻薄本充电。\n\n注意双口同时使用时功率会降到 45W + 20W，给笔记本充电建议单口使用。',
    citations: [
      {
        documentName: '极闪 GX-65W-GaN 产品说明书.pdf',
        snippet: 'USB-C1 单口输出：65W Max；双口同时输出：45W + 20W。',
        score: 0.91
      }
    ]
  }
]

const FALLBACK_REPLY =
  '这个问题我需要再确认一下，已经帮您转接人工客服，稍等片刻。您也可以补充一下具体机型和订单号，我这边再查。'

function generateReply(text: string): { content: string; citations?: ChatMessage['citations']; confidence: number } {
  const hit = REPLY_LIBRARY.find((item) => item.match.test(text))
  if (hit) return { content: hit.reply, citations: hit.citations, confidence: 0.88 }
  return { content: FALLBACK_REPLY, confidence: 0.42 }
}

/* ------------------------------ Auth 模拟状态 ------------------------------ */

/**
 * 模拟 dev 的 provider=mock + expose-debug-code=true 行为：
 * 六位码每次随机生成（不固定 123456）、一次性校验、新发送即作废旧码。
 * debugCode 不进审计，prod 环境后端不会回传该字段。
 */
let activeSmsCode = ''
let activeSmsPhone = ''

function issueSmsCode(phone: string): string {
  const code = String(Math.floor(100000 + Math.random() * 900000))
  activeSmsCode = code
  activeSmsPhone = phone
  return code
}

function consumeSmsCode(phone: string, code: string): boolean {
  if (!activeSmsCode || activeSmsPhone !== phone || activeSmsCode !== code) return false
  // 一次性校验：验证通过即作废
  activeSmsCode = ''
  activeSmsPhone = ''
  return true
}

/* ------------------------------ 分发 ------------------------------ */

export async function mockDispatch(config: MockConfig): Promise<unknown> {
  await delay()

  const method = config.method.toUpperCase()
  const { path, action } = normalize(config.url)
  const query = { ...(config.params || {}) }
  const body = (config.data || {}) as Record<string, unknown>

  /* ---- Auth（路径与字段按 auth-v1 契约） ---- */
  const smsSend = matchPath('/v1/stores/:slug/auth/sms-codes', path)
  if (method === 'POST' && smsSend) {
    const phone = String(body.phone || '')
    if (!/^1[3-9]\d{9}$/.test(phone)) throw new MockError('手机号格式不正确', 10001, 400)
    const debugCode = issueSmsCode(phone)
    const res: SmsResult = { expiresIn: 300, retryAfter: 60, debugCode }
    return res
  }
  const consumerLogin = matchPath('/v1/stores/:slug/auth/consumer/login', path)
  if (method === 'POST' && consumerLogin) {
    const phone = String(body.phone || '')
    const code = String(body.code || '')
    if (!/^\d{6}$/.test(code)) throw new MockError('请求参数无效', 10001, 400)
    if (!consumeSmsCode(phone, code)) throw new MockError('验证码错误或已过期', 11002, 400)
    return buildTokens(consumerUser)
  }
  if (method === 'POST' && path === '/v1/auth/staff/login') {
    const code = String(body.merchantCode || '')
    const identifier = String(body.identifier || '')
    const password = String(body.password || '')
    if (code !== MERCHANT.code) throw new MockError('商户编码不存在', 10004, 404)
    if (identifier !== 'admin' || password !== 'Admin@123') {
      throw new MockError('账号或密码错误', 11001, 401)
    }
    return buildTokens(staffUser)
  }
  if (method === 'POST' && path === '/v1/auth/refresh') {
    const refreshToken = String(body.refreshToken || '')
    if (!refreshToken.startsWith('mock.refresh.')) {
      throw new MockError('登录状态已失效', 10002, 401)
    }
    const role = refreshToken.includes('merchant_staff') ? staffUser : consumerUser
    return buildTokens(role)
  }
  if (method === 'GET' && path === '/v1/auth/me') {
    return principalOf(staffUser)
  }
  if (method === 'POST' && path === '/v1/auth/logout') {
    return {}
  }

  /* ---- Public（契约确认：公开资源 /v1/stores/{storeSlug}/products，URL 定位租户，无 /public 前缀） ---- */
  const publicList = matchPath('/v1/stores/:slug/products', path)
  if (method === 'GET' && publicList) {
    if (publicList.slug !== MERCHANT.code) throw new MockError('店铺不存在', 10004, 404)
    const keyword = String(query.keyword || '').trim()
    const category = String(query.category || '')
    const sort = String(query.sort || 'recommend')
    let list: ProductSummary[] = products.filter((p) => p.status === 'ON_SALE')
    if (keyword) {
      list = list.filter(
        (p) =>
          p.name.includes(keyword) ||
          (p.brand || '').includes(keyword) ||
          p.tags.some((t) => t.includes(keyword))
      )
    }
    if (category && category !== '全部') {
      list = list.filter((p) => p.categoryName === category)
    }
    if (sort === 'price-asc') list = [...list].sort((a, b) => a.minPrice - b.minPrice)
    if (sort === 'price-desc') list = [...list].sort((a, b) => b.minPrice - a.minPrice)
    return paginate(
      list,
      Number(query.page || 1),
      Number(query.pageSize || 12)
    ) as PageResult<ProductSummary>
  }

  const publicDetail = matchPath('/v1/stores/:slug/products/:id', path)
  if (method === 'GET' && publicDetail) {
    const detail = productDetails[publicDetail.id] || buildFallbackDetail(publicDetail.id)
    if (!detail) throw new MockError('商品不存在', 10004, 404)
    return detail
  }

  /* ---- Product（登录态） ---- */
  if (method === 'GET' && path === '/v1/products') {
    const keyword = String(query.keyword || '').trim()
    const list = keyword ? products.filter((p) => p.name.includes(keyword)) : products
    return paginate(list, Number(query.page || 1), Number(query.pageSize || 10))
  }
  const productDetail = matchPath('/v1/products/:id', path)
  if (method === 'GET' && productDetail) {
    const detail = productDetails[productDetail.id] || buildFallbackDetail(productDetail.id)
    if (!detail) throw new MockError('商品不存在', 20001, 404)
    return detail
  }

  /* ---- Order / Shipment（字段与 order-v1、shipment-v1 契约一致） ---- */
  if (method === 'GET' && path === '/v1/orders') {
    const pageNum = Number(query.pageNum || 1)
    const pageSize = Number(query.pageSize || 10)
    const start = (pageNum - 1) * pageSize
    return {
      records: orders.slice(start, start + pageSize),
      total: orders.length,
      current: pageNum,
      size: pageSize
    }
  }

  // 物流：必须按 orderId 查；订单不存在 / 非本人 / 跨租户统一 404 + 10004
  const shipmentMatch = matchPath('/v1/orders/:id/shipment', path)
  if (method === 'GET' && shipmentMatch) {
    const order = orders.find((o) => o.orderId === shipmentMatch.id)
    if (!order) throw new MockError('资源不存在或无权访问', 10004, 404)
    const found: ShipmentView | undefined = shipments[order.orderId]
    // 订单存在但未发货：契约返回 NOT_SHIPPED + 空轨迹，不报错
    return (
      found ?? { orderId: order.orderId, orderNo: order.orderNo, status: 'NOT_SHIPPED', tracks: [] }
    )
  }

  const orderDetail = matchPath('/v1/orders/:id', path)
  if (method === 'GET' && orderDetail) {
    const order = orders.find((o) => o.orderId === orderDetail.id || o.orderNo === orderDetail.id)
    if (!order) throw new MockError('资源不存在或无权访问', 10004, 404)
    return { ...order, items: orderItems[order.orderId] ?? [] }
  }

  /* ---- Chat ---- */
  if (method === 'GET' && path === '/v1/chat/sessions') {
    const scope = String(query.scope || 'consumer')
    const base = scope === 'merchant' ? merchantSessions : consumerSessions
    return base.map((s) => sessionStoreMeta.get(s.id) || s)
  }

  if (method === 'POST' && path === '/v1/chat/sessions') {
    const session: ChatSession = {
      id: uid('CS'),
      title: String(body.title || '新的咨询'),
      status: 'AGENT',
      lastMessage: '',
      updatedAt: new Date().toISOString(),
      messageCount: 0
    }
    sessionStore.set(session.id, [])
    sessionStoreMeta.set(session.id, session)
    return session
  }

  const sessionMessages = matchPath('/v1/chat/sessions/:id/messages', path)
  if (method === 'GET' && sessionMessages) {
    return sessionStore.get(sessionMessages.id) || []
  }

  if (method === 'POST' && sessionMessages) {
    const sessionId = sessionMessages.id
    const text = String(body.content || '').trim()
    if (!text) throw new MockError('消息内容不能为空', 30001, 400)
    const list = sessionStore.get(sessionId) || []
    const userMsg: ChatMessage = {
      id: uid('M'),
      sessionId,
      role: 'user',
      content: text,
      createdAt: new Date().toISOString()
    }
    const generated = generateReply(text)
    const agentMsg: ChatMessage = {
      id: uid('M'),
      sessionId,
      role: 'assistant',
      content: generated.content,
      createdAt: new Date().toISOString(),
      citations: generated.citations,
      confidence: generated.confidence,
      toolCalls:
        generated.confidence > 0.6
          ? [{ tool: 'knowledge.retrieve', params: { topK: 3 }, latencyMs: 64, success: true }]
          : undefined
    }
    const next = [...list, userMsg, agentMsg]
    sessionStore.set(sessionId, next)
    const meta = sessionStoreMeta.get(sessionId)
    if (meta) {
      sessionStoreMeta.set(sessionId, {
        ...meta,
        lastMessage: generated.content.slice(0, 40),
        updatedAt: new Date().toISOString(),
        messageCount: next.length,
        status: generated.confidence > 0.6 ? meta.status : 'HUMAN'
      })
    }
    return agentMsg
  }

  if (method === 'POST' && path.includes('/v1/chat/sessions/') && action === 'takeover') {
    const id = path.split('/')[4]
    const meta = sessionStoreMeta.get(id)
    if (meta) sessionStoreMeta.set(id, { ...meta, status: 'HUMAN' })
    return { sessionId: id, status: 'HUMAN' }
  }

  if (method === 'POST' && path.includes('/v1/chat/sessions/') && action === 'resume-agent') {
    const id = path.split('/')[4]
    const meta = sessionStoreMeta.get(id)
    if (meta) sessionStoreMeta.set(id, { ...meta, status: 'AGENT' })
    return { sessionId: id, status: 'AGENT' }
  }

  /* ---- Knowledge / Task ---- */
  if (method === 'GET' && path === '/v1/knowledge-bases') {
    return knowledgeBases
  }

  if (method === 'GET' && path === '/v1/document-tasks') {
    return documentTasks as DocumentTask[]
  }

  const taskRetry = matchPath('/v1/document-tasks/:id', path)
  if (method === 'POST' && taskRetry && action === 'retry') {
    return { ...documentTasks[3], status: 'QUEUED', progress: 0, errorMessage: undefined }
  }

  const taskDetail = matchPath('/v1/document-tasks/:id', path)
  if (method === 'GET' && taskDetail) {
    return documentTasks.find((t) => t.id === taskDetail.id) || documentTasks[0]
  }

  /* ---- Merchant overview（接口待后端补齐，先占位） ---- */
  if (method === 'GET' && path === '/v1/merchant/overview') {
    return merchantOverview
  }

  throw new MockError(`Mock 未匹配到接口：${method} ${path}`, 40400, 404)
}
