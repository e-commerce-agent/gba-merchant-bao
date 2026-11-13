import type {
  ChatMessage,
  ChatSession,
  DocumentTask,
  KnowledgeBase,
  MerchantOverview
} from '@/api/types'

/**
 * Mock 数据仓库（仅覆盖后端尚未交付的能力：认证演示、会话、知识库、文档任务、工作台指标）。
 * 商品 / 库存 / 订单 / 物流 已按 OpenAPI 契约交付，前端直连真实接口，
 * 这里不再保留假数据，避免与契约漂移。
 */

export const MERCHANT = {
  id: '1',
  code: 'M_3C_DEMO',
  name: '极闪数码旗舰店（广州）',
  city: '广州',
  category: '3C 配件'
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
