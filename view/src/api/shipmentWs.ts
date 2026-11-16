import { shouldMock } from './request'
import type { ShipmentStatus, ShipmentTrack } from './types'

/**
 * 物流 WebService（shipment.wsdl）调用层。
 *
 * 后端按 WSDL 暴露 SOAP 1.1 服务（document/literal）：
 *   POST /services/shipment，SOAPAction 分别为 ping / queryShipmentByOrderNo；
 *   查询返回 ShipmentSummary（运单摘要 + track 轨迹列表），失败抛 SOAP Fault。
 *
 * 浏览器无法用 axios 的 JSON 拦截器处理 XML，这里用 fetch 直发 SOAP 信封，
 * DOMParser 解析响应；WSDL elementFormDefault=unqualified，
 * 响应元素为无前缀局部名，按 localName 提取即可。
 *
 * 走 vite 代理：开发环境 /services → VITE_PROXY_TARGET（同 /api），生产同域直连。
 */

export const SHIPMENT_WS_ENDPOINT = '/services/shipment'

const SOAP_NS = 'http://schemas.xmlsoap.org/soap/envelope/'
const TNS = 'https://gba.merchantbao/shipment/v1'

/**
 * 服务间凭证（SOAP Header 的 tns:serviceToken）。
 *
 * 后端设计上凭证"不进前端、不进仓库明文"，生产应由网关/BFF 在转发时注入；
 * 课程演示环境没有网关，只能由本地 .env.development.local 提供（不进仓库），
 * 页面顶部的「服务在线」探针会明确暴露缺凭证的失败原因，避免误判成接口没做。
 */
const SERVICE_TOKEN = String(import.meta.env.VITE_SHIPMENT_WS_TOKEN || '').trim()

export interface ShipmentWsSummary {
  orderNo: string
  status: ShipmentStatus | string
  carrierName: string
  carrierCode: string
  trackingNo: string
  lastEventTime: string
  track: ShipmentTrack[]
}

export class SoapFaultError extends Error {
  faultCode: string
  constructor(message: string, faultCode = 'SOAP-FAULT') {
    super(message)
    this.name = 'SoapFaultError'
    this.faultCode = faultCode
  }
}

function envelope(bodyXml: string): string {
  const header = SERVICE_TOKEN
    ? `<tns:serviceToken>${SERVICE_TOKEN}</tns:serviceToken>`
    : ''
  return `<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="${SOAP_NS}" xmlns:tns="${TNS}">
  <SOAP-ENV:Header>${header}</SOAP-ENV:Header>
  <SOAP-ENV:Body>
    ${bodyXml}
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>`
}

/** 按 localName 找第一个元素（不依赖命名空间前缀） */
function first(el: Element | Document, localName: string): Element | null {
  const list = el.getElementsByTagNameNS('*', localName)
  return list.length ? list[0] : null
}

function textOf(el: Element | Document, localName: string): string {
  const node = first(el, localName)
  return node?.textContent?.trim() || ''
}

/** 解析 SOAP Fault；无 Fault 返回 null */
function parseFault(doc: Document): SoapFaultError | null {
  const fault = first(doc, 'Fault')
  if (!fault) return null
  const message = textOf(fault, 'faultstring') || 'WebService 调用失败'
  const code = textOf(fault, 'faultcode') || 'SOAP-FAULT'
  return new SoapFaultError(message, code)
}

async function soapCall(soapAction: string, bodyXml: string): Promise<Document> {
  let res: Response
  try {
    res = await fetch(SHIPMENT_WS_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'text/xml; charset=utf-8',
        SOAPAction: `"${soapAction}"`
      },
      body: envelope(bodyXml)
    })
  } catch {
    throw new SoapFaultError('无法连接物流 WebService，请确认后端已启动', 'NETWORK')
  }

  const text = await res.text()
  const doc = new DOMParser().parseFromString(text, 'text/xml')
  if (doc.getElementsByTagName('parsererror').length) {
    throw new SoapFaultError(`WebService 响应不是合法 XML（HTTP ${res.status}）`, 'PARSE')
  }

  const fault = parseFault(doc)
  if (fault) throw fault
  if (!res.ok) {
    throw new SoapFaultError(`WebService 调用失败（HTTP ${res.status}）`, `HTTP-${res.status}`)
  }
  return doc
}

/** 连通性探测：WSDL ping 操作 */
export async function pingShipmentService(): Promise<string> {
  if (shouldMock(SHIPMENT_WS_ENDPOINT)) {
    return 'ShipmentWebService is alive (mock)'
  }
  const doc = await soapCall('ping', '<tns:ping/>')
  return textOf(doc, 'return')
}

/** 按订单号查运单（WSDL queryShipmentByOrderNo）；未发货时后端返回 NOT_SHIPPED 空轨迹 */
export async function queryShipmentByOrderNo(
  merchantCode: string,
  orderNo: string
): Promise<ShipmentWsSummary> {
  if (shouldMock(SHIPMENT_WS_ENDPOINT)) {
    return {
      orderNo,
      status: 'IN_TRANSIT',
      carrierName: '顺丰速运',
      carrierCode: 'SF',
      trackingNo: 'SF1000000000301',
      lastEventTime: new Date().toISOString(),
      track: [
        { eventTime: new Date().toISOString(), status: 'IN_TRANSIT', description: '快件已到达广州白云集散中心', location: '广州' },
        { eventTime: new Date(Date.now() - 36e5 * 20).toISOString(), status: 'IN_TRANSIT', description: '快件已从广州天河仓发出', location: '广州' },
        { eventTime: new Date(Date.now() - 36e5 * 26).toISOString(), status: 'IN_TRANSIT', description: '顺丰已揽收', location: '广州' }
      ]
    }
  }

  const escape = (v: string) => v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const doc = await soapCall(
    'queryShipmentByOrderNo',
    `<tns:queryShipmentByOrderNo>
      <merchantCode>${escape(merchantCode)}</merchantCode>
      <orderNo>${escape(orderNo)}</orderNo>
    </tns:queryShipmentByOrderNo>`
  )

  // WSDL 里 ShipmentSummary 是 complexType 名；线上响应元素是 <return>（内联字段无命名空间）
  const summary = first(doc, 'return') || first(doc, 'ShipmentSummary')
  if (!summary) {
    throw new SoapFaultError('响应缺少运单数据（return 为空，可能是订单不存在）', 'EMPTY')
  }

  const tracks: ShipmentTrack[] = []
  const trackNodes = summary.getElementsByTagNameNS('*', 'track')
  for (const node of Array.from(trackNodes)) {
    tracks.push({
      eventTime: textOf(node, 'eventTime'),
      status: textOf(node, 'status'),
      description: textOf(node, 'description'),
      location: textOf(node, 'location') || undefined
    })
  }

  return {
    orderNo: textOf(summary, 'orderNo'),
    status: textOf(summary, 'status') || 'NOT_SHIPPED',
    carrierName: textOf(summary, 'carrierName'),
    carrierCode: textOf(summary, 'carrierCode'),
    trackingNo: textOf(summary, 'trackingNo'),
    lastEventTime: textOf(summary, 'lastEventTime'),
    track: tracks
  }
}
