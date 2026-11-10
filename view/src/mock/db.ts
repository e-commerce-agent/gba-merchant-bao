import type {
  ChatMessage,
  ChatSession,
  DocumentTask,
  KnowledgeBase,
  MerchantOverview,
  OrderItemView,
  OrderSummary,
  ProductDetail,
  ProductSummary,
  ShipmentView,
  Sku
} from '@/api/types'

/**
 * Mock 数据仓库。
 * 结构刻意对齐 db/migration 的表设计（product / sku / inventory / order / shipment /
 * chat_session / chat_message / knowledge_base / document_task），
 * 后端接口就绪后这份文件整体删除即可，页面与 api 层不用改。
 */

export const MERCHANT = {
  id: '1',
  code: 'M_3C_DEMO',
  name: '极闪数码旗舰店（广州）',
  city: '广州',
  category: '3C 配件'
}

export function sku(
  id: string,
  productId: string,
  name: string,
  attributes: Array<{ name: string; value: string }>,
  price: number,
  stockStatus: Sku['stockStatus'],
  stockQty: number
): Sku {
  return {
    id,
    productId,
    skuCode: `SKU-${id}`,
    name,
    attributes,
    price,
    currency: 'CNY',
    stockStatus,
    stockQty
  }
}

export const products: ProductSummary[] = [
  {
    id: 'P1001',
    merchantId: MERCHANT.id,
    name: '极闪 65W 氮化镓双口充电器',
    subtitle: '一充电器搞定手机、平板、轻薄本',
    categoryName: '充电器',
    brand: '极闪',
    model: 'GX-65W-GaN',
    status: 'ON_SALE',
    thumb: 'charger',
    minPrice: 129,
    maxPrice: 149,
    currency: 'CNY',
    skuCount: 4,
    tags: ['氮化镓', '双口输出', 'PD3.0'],
    stockStatus: 'IN_STOCK'
  },
  {
    id: 'P1002',
    merchantId: MERCHANT.id,
    name: '极闪 100W USB-C 编织数据线 2m',
    subtitle: '支持 100W 快充与 480Mbps 数据传输',
    categoryName: '数据线',
    brand: '极闪',
    model: 'GX-C100-2M',
    status: 'ON_SALE',
    thumb: 'cable',
    minPrice: 49,
    maxPrice: 59,
    currency: 'CNY',
    skuCount: 3,
    tags: ['100W', '编织线', 'E-Marker'],
    stockStatus: 'IN_STOCK'
  },
  {
    id: 'P1003',
    merchantId: MERCHANT.id,
    name: '磁力方 磁吸无线充电宝 10000mAh',
    subtitle: '磁吸即充，支持边充边用',
    categoryName: '移动电源',
    brand: '磁力方',
    model: 'MC-PB10',
    status: 'ON_SALE',
    thumb: 'powerbank',
    minPrice: 219,
    maxPrice: 219,
    currency: 'CNY',
    skuCount: 2,
    tags: ['磁吸', '10000mAh', '20W'],
    stockStatus: 'LOW_STOCK'
  },
  {
    id: 'P1004',
    merchantId: MERCHANT.id,
    name: '声谷 TWS 半入耳蓝牙耳机',
    subtitle: '双设备连接，通话降噪',
    categoryName: '耳机',
    brand: '声谷',
    model: 'SG-TWS3',
    status: 'ON_SALE',
    thumb: 'earphone',
    minPrice: 199,
    maxPrice: 239,
    currency: 'CNY',
    skuCount: 3,
    tags: ['蓝牙 5.3', '双设备', '半入耳'],
    stockStatus: 'IN_STOCK'
  },
  {
    id: 'P1005',
    merchantId: MERCHANT.id,
    name: '星驰 45W 车载快充充电器',
    subtitle: '双口车充，支持 PD + QC 协议',
    categoryName: '车载充电',
    brand: '星驰',
    model: 'SC-CC45',
    status: 'ON_SALE',
    thumb: 'car',
    minPrice: 89,
    maxPrice: 89,
    currency: 'CNY',
    skuCount: 2,
    tags: ['45W', '双口', '车规级'],
    stockStatus: 'IN_STOCK'
  },
  {
    id: 'P1006',
    merchantId: MERCHANT.id,
    name: '磁力方 15W 磁吸无线充电器',
    subtitle: '桌面磁吸支架，横竖都能充',
    categoryName: '无线充',
    brand: '磁力方',
    model: 'MC-W15',
    status: 'ON_SALE',
    thumb: 'wireless',
    minPrice: 109,
    maxPrice: 109,
    currency: 'CNY',
    skuCount: 1,
    tags: ['磁吸', '15W', '支架'],
    stockStatus: 'OUT_OF_STOCK'
  },
  {
    id: 'P1007',
    merchantId: MERCHANT.id,
    name: '星驰 120W 桌面充电站',
    subtitle: '四口同时输出，桌面收纳一体',
    categoryName: '充电器',
    brand: '星驰',
    model: 'SC-DK120',
    status: 'ON_SALE',
    thumb: 'dock',
    minPrice: 299,
    maxPrice: 329,
    currency: 'CNY',
    skuCount: 2,
    tags: ['120W', '四口', '桌面'],
    stockStatus: 'IN_STOCK'
  },
  {
    id: 'P1008',
    merchantId: MERCHANT.id,
    name: '极闪 20W 苹果快充套装',
    subtitle: '充电头 + 编织线，开箱即用',
    categoryName: '充电器',
    brand: '极闪',
    model: 'GX-20W-SET',
    status: 'ON_SALE',
    thumb: 'charger',
    minPrice: 79,
    maxPrice: 79,
    currency: 'CNY',
    skuCount: 2,
    tags: ['20W', '套装', 'MFi 线材'],
    stockStatus: 'IN_STOCK'
  }
]

export const productDetails: Record<string, ProductDetail> = {
  P1001: {
    ...products[0],
    description:
      '采用第三代氮化镓（GaN）方案，体积比同功率传统充电器缩小约 40%。双口智能分配功率，单口最高 65W，可满足轻薄笔记本、平板与手机同时充电。',
    sellingPoints: [
      '单口 65W / 双口 45W + 20W 智能分配',
      '支持 PD3.0、QC4+、PPS 快充协议',
      '折叠插脚，出差携带不刮包',
      '内置过温、过流、过压、短路四重保护'
    ],
    compatibility: [
      '支持 PD 协议的轻薄笔记本：MacBook Air、MateBook 等',
      '支持 PD/QC 快充的手机与平板',
      '接口类型：USB-C × 1、USB-A × 1',
      '快充协议：PD3.0 / QC4+ / PPS / AFC'
    ],
    specs: [
      { label: '最大功率', value: '65W' },
      { label: '输入', value: '100-240V 宽电压' },
      { label: '输出接口', value: 'USB-C × 1、USB-A × 1' },
      { label: '协议', value: 'PD3.0 / QC4+ / PPS / AFC' },
      { label: '尺寸', value: '52 × 52 × 30 mm' },
      { label: '重量', value: '约 112 g' }
    ],
    skus: [
      sku('S100101', 'P1001', '白色 · 国标插头', [{ name: '颜色', value: '白色' }, { name: '插头规格', value: '国标' }], 129, 'IN_STOCK', 128),
      sku('S100102', 'P1001', '白色 · 英标插头（港版）', [{ name: '颜色', value: '白色' }, { name: '插头规格', value: '英标' }], 139, 'IN_STOCK', 46),
      sku('S100103', 'P1001', '黑色 · 国标插头', [{ name: '颜色', value: '黑色' }, { name: '插头规格', value: '国标' }], 129, 'LOW_STOCK', 9),
      sku('S100104', 'P1001', '黑色 · 英标插头（港版）', [{ name: '颜色', value: '黑色' }, { name: '插头规格', value: '英标' }], 139, 'OUT_OF_STOCK', 0)
    ],
    afterSale: '7 天无理由退换，18 个月只换不修；非人为损坏免费换新。'
  },
  P1002: {
    ...products[1],
    description: '采用尼龙编织外被，通过 10000 次弯折测试。内置 E-Marker 芯片，可稳定承载 100W 功率。',
    sellingPoints: ['100W 大功率', '尼龙编织抗弯折', '480Mbps 数据传输'],
    compatibility: ['USB-C 接口设备', '支持 PD 快充的手机 / 笔记本'],
    specs: [
      { label: '长度', value: '2 m' },
      { label: '最大功率', value: '100W' },
      { label: '数据传输', value: '480 Mbps' },
      { label: '接口', value: 'USB-C to USB-C' }
    ],
    skus: [
      sku('S100201', 'P1002', '1m · 白色', [{ name: '长度', value: '1m' }, { name: '颜色', value: '白色' }], 49, 'IN_STOCK', 320),
      sku('S100202', 'P1002', '2m · 黑色', [{ name: '长度', value: '2m' }, { name: '颜色', value: '黑色' }], 59, 'IN_STOCK', 210),
      sku('S100203', 'P1002', '2m · 白色', [{ name: '长度', value: '2m' }, { name: '颜色', value: '白色' }], 59, 'IN_STOCK', 88)
    ],
    afterSale: '7 天无理由退换，12 个月保修。'
  },
  P1003: {
    ...products[2],
    description: '磁吸对位自动吸附，支持 20W 有线快充与 15W 无线输出，可折叠支架边充边看。',
    sellingPoints: ['磁吸对位', '10000mAh 容量', '20W 有线 + 15W 无线'],
    compatibility: ['支持磁吸的手机（iPhone 12 及以后）', '其他机型可通过附赠磁吸环使用'],
    specs: [
      { label: '容量', value: '10000mAh' },
      { label: '有线输出', value: '20W' },
      { label: '无线输出', value: '15W' },
      { label: '重量', value: '约 210 g' }
    ],
    skus: [
      sku('S100301', 'P1003', '深空灰', [{ name: '颜色', value: '深空灰' }], 219, 'LOW_STOCK', 12),
      sku('S100302', 'P1003', '雾白', [{ name: '颜色', value: '雾白' }], 219, 'OUT_OF_STOCK', 0)
    ],
    afterSale: '7 天无理由退换，12 个月保修；电池类不支持无理由二次销售。'
  }
}

/**
 * 订单/物流 Mock：字段与 order-v1 / shipment-v1 契约一一对应。
 * 订单 id 刻意与 devdata（R__040 / R__030）对齐，切到真实接口时看到的是同一批单：
 *   2100000000000302 = 运输中（有运单 + 3 段轨迹）
 *   2100000000000301 = 已完成（有运单 + 已签收轨迹）
 *   2100000000000303 = 待发货（无运单记录 → 后端合成 NOT_SHIPPED）
 */
export const orders: OrderSummary[] = [
  {
    orderId: '2100000000000302',
    orderNo: 'DEV-2026-0002',
    status: 'SHIPPED',
    payStatus: 'PAID',
    totalAmount: 268,
    paidAmount: 268,
    currency: 'CNY',
    placedAt: '2026-10-28T06:22:00Z'
  },
  {
    orderId: '2100000000000301',
    orderNo: 'DEV-2026-0001',
    status: 'COMPLETED',
    payStatus: 'PAID',
    totalAmount: 219,
    paidAmount: 219,
    currency: 'CNY',
    placedAt: '2026-10-19T03:08:00Z'
  },
  {
    orderId: '2100000000000303',
    orderNo: 'DEV-2026-0003',
    status: 'PENDING_SHIPMENT',
    payStatus: 'PAID',
    totalAmount: 79,
    paidAmount: 79,
    currency: 'CNY',
    placedAt: '2026-10-30T01:45:00Z'
  }
]

/** 下单时的成交快照（名称/属性/单价均为冻结值） */
export const orderItems: Record<string, OrderItemView[]> = {
  '2100000000000302': [
    {
      itemId: '2200000000000301',
      productId: 'P1001',
      skuId: 'S100101',
      productName: '极闪 65W 氮化镓双口充电器',
      skuAttributes: '颜色:白色;插头:国标',
      unitPrice: 129,
      quantity: 1,
      amount: 129
    },
    {
      itemId: '2200000000000302',
      productId: 'P1002',
      skuId: 'S100201',
      productName: '极闪 100W USB-C 编织数据线 2m',
      skuAttributes: '长度:2m;颜色:黑色',
      unitPrice: 59,
      quantity: 2,
      amount: 118
    }
  ],
  '2100000000000301': [
    {
      itemId: '2200000000000311',
      productId: 'P1003',
      skuId: 'S100301',
      productName: '磁力方 磁吸无线充电宝 10000mAh',
      skuAttributes: '颜色:深空灰',
      unitPrice: 219,
      quantity: 1,
      amount: 219
    }
  ],
  '2100000000000303': [
    {
      itemId: '2200000000000321',
      productId: 'P1004',
      skuId: 'S100401',
      productName: '极闪 20W 苹果快充套装',
      skuAttributes: '颜色:白色;插头:国标',
      unitPrice: 79,
      quantity: 1,
      amount: 79
    }
  ]
}

/** 运单按 orderId 索引；没有运单的订单由 mock 按契约合成 NOT_SHIPPED */
export const shipments: Record<string, ShipmentView> = {
  '2100000000000302': {
    orderId: '2100000000000302',
    orderNo: 'DEV-2026-0002',
    status: 'IN_TRANSIT',
    carrierName: '顺丰速运',
    carrierCode: 'SF',
    trackingNo: 'SF1000000000301',
    estimatedDeliveryAt: '2026-11-12T10:00:00Z',
    tracks: [
      {
        eventTime: '2026-11-10T01:40:00Z',
        status: 'IN_TRANSIT',
        description: '包裹已从深圳集散中心发出，派送中',
        location: '深圳'
      },
      {
        eventTime: '2026-11-09T02:00:00Z',
        status: 'IN_TRANSIT',
        description: '包裹到达深圳集散中心',
        location: '深圳'
      },
      {
        eventTime: '2026-11-08T02:00:00Z',
        status: 'IN_TRANSIT',
        description: '包裹已从广州转运中心发出',
        location: '广州'
      }
    ]
  },
  '2100000000000301': {
    orderId: '2100000000000301',
    orderNo: 'DEV-2026-0001',
    status: 'DELIVERED',
    carrierName: '顺丰速运',
    carrierCode: 'SF',
    trackingNo: 'SF1000000000302',
    deliveredAt: '2026-10-22T05:30:00Z',
    tracks: [
      {
        eventTime: '2026-10-22T05:30:00Z',
        status: 'DELIVERED',
        description: '包裹已签收，签收人：本人',
        location: '广州'
      },
      {
        eventTime: '2026-10-21T07:10:00Z',
        status: 'IN_TRANSIT',
        description: '包裹已从东莞转运中心发出',
        location: '东莞'
      }
    ]
  }
}

const now = Date.now()
const iso = (minutesAgo: number) => new Date(now - minutesAgo * 60000).toISOString()

export const consumerMessages: ChatMessage[] = [
  {
    id: 'M1',
    sessionId: 'CS1001',
    role: 'user',
    content: '我这个 65W 充电器能给 MacBook Air 充电吗？',
    createdAt: iso(12)
  },
  {
    id: 'M2',
    sessionId: 'CS1001',
    role: 'assistant',
    content:
      '可以。极闪 65W 氮化镓双口充电器支持 PD3.0 协议，单口输出最高 65W，MacBook Air（M 系列）原装充电器为 30W–35W，用它充电完全够用，充电速度还会更快一些。\n\n需要注意：双口同时使用时功率会重新分配（45W + 20W），给笔记本充电建议只插一个口。',
    createdAt: iso(11),
    confidence: 0.92,
    citations: [
      {
        documentName: '极闪 GX-65W-GaN 产品说明书.pdf',
        snippet: 'USB-C1 单口输出：65W Max；USB-C1 + USB-A 同时输出：45W + 20W。',
        score: 0.91
      },
      {
        documentName: '兼容性说明-笔记本机型.md',
        snippet: '支持 PD 协议的轻薄笔记本，含 MacBook Air、MateBook 系列。',
        score: 0.87
      }
    ],
    toolCalls: [
      { tool: 'product.query', params: { productId: 'P1001' }, latencyMs: 42, success: true },
      { tool: 'inventory.availability', params: { skuId: 'S100101' }, latencyMs: 28, success: true }
    ]
  },
  {
    id: 'M3',
    sessionId: 'CS1001',
    role: 'user',
    content: '那港版英标插头和国标有什么区别？价格一样吗？',
    createdAt: iso(8)
  },
  {
    id: 'M4',
    sessionId: 'CS1001',
    role: 'assistant',
    content:
      '外观和功率完全一致，区别只在插脚形状：国标是两脚扁插，英标（港版）是三脚方插，适合在香港、英国、新加坡等地区使用。\n\n价格上英标比国标贵 ¥10（¥139 / ¥129）。目前白色英标有货，黑色英标暂时缺货，需要的话可以先加购白色款。',
    createdAt: iso(7),
    confidence: 0.88,
    citations: [
      {
        documentName: '极闪 产品规格对照表.xlsx',
        snippet: '国标 GB 两脚扁插；英标 BS 1363 三脚方插。',
        score: 0.9
      }
    ],
    toolCalls: [
      { tool: 'inventory.availability', params: { skuId: 'S100102' }, latencyMs: 31, success: true },
      { tool: 'inventory.availability', params: { skuId: 'S100104' }, latencyMs: 26, success: true }
    ]
  }
]

export const consumerSessions: ChatSession[] = [
  {
    id: 'CS1001',
    title: '65W 充电器兼容性咨询',
    status: 'AGENT',
    lastMessage: '价格上英标比国标贵 ¥10（¥139 / ¥129）',
    updatedAt: iso(7),
    messageCount: 4
  },
  {
    id: 'CS1002',
    title: '订单物流查询',
    status: 'AGENT',
    lastMessage: '您的订单已到广州白云集散中心',
    updatedAt: iso(180),
    messageCount: 6
  },
  {
    id: 'CS1003',
    title: '耳机双设备连接设置',
    status: 'CLOSED',
    lastMessage: '问题已解决，感谢您的咨询',
    updatedAt: iso(1440),
    messageCount: 8
  }
]

export const merchantSessions: ChatSession[] = [
  {
    id: 'CS2001',
    title: '充电宝磁吸不充电',
    status: 'HUMAN',
    lastMessage: '已为您登记换新，明天安排寄出',
    updatedAt: iso(4),
    messageCount: 12,
    consumerName: '陈先生',
    confidence: 0.41,
    unread: 2
  },
  {
    id: 'CS2002',
    title: '65W 充电器兼容性咨询',
    status: 'AGENT',
    lastMessage: '双口同时使用功率会重新分配',
    updatedAt: iso(9),
    messageCount: 4,
    consumerName: '李小姐',
    confidence: 0.92
  },
  {
    id: 'CS2003',
    title: '港版插头是否适配香港插座',
    status: 'AGENT',
    lastMessage: '英标三脚方插可直接使用',
    updatedAt: iso(46),
    messageCount: 7,
    consumerName: '黄先生',
    confidence: 0.66
  },
  {
    id: 'CS2004',
    title: '批发采购报价',
    status: 'CLOSED',
    lastMessage: '已发送批量报价单',
    updatedAt: iso(320),
    messageCount: 9,
    consumerName: '周小姐',
    confidence: 0.85
  }
]

export const merchantMessages: ChatMessage[] = [
  {
    id: 'MM1',
    sessionId: 'CS2001',
    role: 'user',
    content: '我的充电宝磁吸上去没反应，灯也不亮',
    createdAt: iso(22)
  },
  {
    id: 'MM2',
    sessionId: 'CS2001',
    role: 'assistant',
    content:
      '先帮您排查一下：请确认充电宝电量是否充足（长按电源键查看指示灯），以及手机是否带有磁吸环。如果指示灯不亮，可能是电量耗尽，建议先用有线充电 30 分钟再试。',
    createdAt: iso(21),
    confidence: 0.43,
    citations: [
      {
        documentName: '磁力方 充电宝常见问题 FAQ.md',
        snippet: '指示灯不亮时，请先有线充电 30 分钟激活电池。',
        score: 0.72
      }
    ],
    toolCalls: [{ tool: 'order.query', params: { orderId: 'O10002' }, latencyMs: 35, success: true }]
  },
  {
    id: 'MM3',
    sessionId: 'CS2001',
    role: 'user',
    content: '充了半小时还是不行，昨天刚收到的',
    createdAt: iso(10)
  },
  {
    id: 'MM4',
    sessionId: 'CS2001',
    role: 'human',
    content: '您好，我是人工客服小吴。已核实您的订单在保修期内，这种情况可以直接换新。已为您登记，明天上午安排寄出，运费我们承担。',
    createdAt: iso(4)
  }
]

export const documentTasks: DocumentTask[] = [
  {
    id: 'DT3001',
    fileName: '极闪 GX-65W-GaN 产品说明书.pdf',
    fileSize: 2_412_800,
    status: 'SUCCEEDED',
    progress: 100,
    chunkCount: 42,
    createdAt: iso(180)
  },
  {
    id: 'DT3002',
    fileName: '兼容性说明-笔记本机型.md',
    fileSize: 18_400,
    status: 'SUCCEEDED',
    progress: 100,
    chunkCount: 12,
    createdAt: iso(176)
  },
  {
    id: 'DT3003',
    fileName: '售后政策 2026 版.docx',
    fileSize: 486_000,
    status: 'EMBEDDING',
    progress: 68,
    chunkCount: 0,
    createdAt: iso(6)
  },
  {
    id: 'DT3004',
    fileName: '香港地区插头标准对照表.xlsx',
    fileSize: 92_000,
    status: 'FAILED',
    progress: 32,
    chunkCount: 0,
    createdAt: iso(48),
    errorMessage: '解析失败：表格存在合并单元格，无法切分'
  }
]

export const knowledgeBases: KnowledgeBase[] = [
  {
    id: 'KB001',
    name: '产品说明书库',
    description: '充电器、数据线、移动电源的官方说明书与参数表',
    documentCount: 12,
    chunkCount: 386,
    updatedAt: iso(60)
  },
  {
    id: 'KB002',
    name: '售后与物流政策',
    description: '退换货规则、保修范围、承运商时效',
    documentCount: 5,
    chunkCount: 74,
    updatedAt: iso(320)
  }
]

export const merchantOverview: MerchantOverview = {
  sessionTotal: 268,
  sessionDelta: 12.4,
  autoResolvedRate: 0.78,
  takeoverCount: 37,
  avgFirstResponseSec: 3.2,
  pendingTasks: 2,
  lowConfidenceSessions: 5,
  trend: [
    { date: '10-27', sessions: 28, takeovers: 4 },
    { date: '10-28', sessions: 34, takeovers: 5 },
    { date: '10-29', sessions: 31, takeovers: 3 },
    { date: '10-30', sessions: 42, takeovers: 6 },
    { date: '10-31', sessions: 38, takeovers: 5 },
    { date: '11-01', sessions: 46, takeovers: 8 },
    { date: '11-02', sessions: 49, takeovers: 6 }
  ]
}
