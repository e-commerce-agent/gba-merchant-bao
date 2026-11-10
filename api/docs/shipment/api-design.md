# 物流模块 API 设计

> 状态：已实现（联调态，2026-11-10）。REST 契约机读版 `docs/api/shipment-v1.openapi.yaml` 已由实际 Springdoc `shipment-v1` 分组导出（替换早前的评审稿，见 [verification.md](verification.md)）；本文同时登记 SOAP WebService 契约。统一响应 `{code, message, data, requestId}`，成功 `code=0`；IDs 字符串化、时间 UTC；私有响应 `Cache-Control: no-store`。

## 1. REST 契约

### GET /api/v1/orders/{orderId}/shipment

按已授权订单查询物流。单操作，只读。

| 项 | 值 |
| --- | --- |
| 认证 | `Authorization: Bearer <access>`（10002） |
| 主体范围 | 消费者=本人订单；员工=本商户订单（Service/SQL 限定） |
| 归属校验 | 订单模块真实归属查询；集成前 fixture 联调，最终验收接真实授权 |
| 不存在/越权 | 统一 404/10004（三者不可区分） |

**成功响应 200**（含未发货业务态）：

```json
{
  "code": 0,
  "message": "OK",
  "requestId": "req-uuid",
  "data": {
    "orderId": "7230...",
    "orderNo": "SO202611070001",
    "status": "IN_TRANSIT",
    "carrierName": "顺丰速运",
    "carrierCode": "SF",
    "trackingNo": "SF1234567890",
    "estimatedDeliveryAt": "2026-11-09T12:00:00Z",
    "deliveredAt": null,
    "tracks": [
      { "eventTime": "2026-11-07T02:30:00Z", "status": "IN_TRANSIT",
        "description": "包裹已从广州转运中心发出", "location": "广州" },
      { "eventTime": "2026-11-07T08:10:00Z", "status": "IN_TRANSIT",
        "description": "包裹到达深圳集散点", "location": "深圳" }
    ]
  }
}
```

- 无物流记录：`status=NOT_SHIPPED`，`tracks=[]`，承运商/运单/时间字段缺省（null），不编造预计送达。
- `estimatedDeliveryAt` 仅数据提供时返回。

**错误**（全部复用公共段；12xxx 预留）：

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | 10001 | orderId 非法 |
| 401 | 10002 | 无效/缺失会话 |
| 403 | 10003 | 角色越权 |
| 404 | 10004 | 订单不存在/跨租户/非本人 |
| 500 | 10500 | 非预期错误 |

**不提供的入口**：按 trackingNo 的任何独立查询（公开或私有），防运单号绕过订单授权。

## 2. SOAP WebService 契约（课程分布式框架落点）

### 2.1 端点与 WSDL

| 项 | 值 |
| --- | --- |
| 实现 | Apache CXF `cxf-spring-boot-starter-jaxws`（4.x，Jakarta 兼容 Boot 3.5） |
| 端点地址 | `/services/shipment`（SOAP 1.1） |
| WSDL | `GET /services/shipment?wsdl` |
| PortType | `ShipmentWebServicePort` |
| targetNamespace | `https://gba.merchantbao/shipment/v1` |
| OpenAPI | `/services/*` 不进入 Springdoc 扫描，避免与 REST 契约混淆 |

### 2.2 操作

```text
String ping()
  — 连通性/课程演示；返回固定问候串，不触库。

ShipmentSummaryDTO queryShipmentByOrderNo(String merchantCode, String orderNo)
  — 按商户编码+订单号返回物流摘要：
    status / carrierName / carrierCode / trackingNo /
    lastEventTime / tracks[]（eventTime, status, description, location）
  — 内部复用与 REST 相同的 ShipmentQueryService，脱敏规则同源。
  — merchantCode+orderNo 不存在或跨商户 → SOAP Fault .NOT_FOUND，不泄露存在性。
```

### 2.3 信任模型与凭证

1. SOAP 端点是**独立信任域**：服务间凭证（配置注入的 server-to-server token）随 SOAP Header 传递，服务端校验失败统一 Fault，不返回数据。
2. 凭证不进前端、不进仓库明文配置（与 JWT 密钥同用 .env/忽略配置管理）；泄露视为越权能力；生产不开放。
3. 端点**不接**浏览器 Bearer 会话；REST 授权边界不被绕过——SOAP 客户端模拟外部承运商系统，本来就没有浏览器会话。
4. 定位：课程「分布式框架初识」演示 + 模拟与外部承运商系统对接；不开放为生产对公能力。

### 2.4 与 WebSocket 的区分

老师硬性要求为 WebService（SOAP/WSDL 同步调用）；本模块不实现 WebSocket。技术图与文档统一按 WebService 口径。

## 3. 前端接入

- REST 契约评审通过后，前端在 Mock 模式按 `docs/api/shipment-v1.openapi.yaml` 补订单详情物流页（轨迹时间线），契约路径/字段以此为准。
- 前端不接触 SOAP 端点与服务间凭证。
