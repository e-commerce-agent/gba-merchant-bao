# Design

> 状态：契约与提案评审稿（2026-11-07）。依据 module-roadmap 第 5 节与第 8 节统一交付标准编写；实现细节在实现阶段按评审结果冻结。

## Context

| 已核对位置 | 当前事实与本次影响 |
| --- | --- |
| `api/docs/architecture/module-roadmap.md` 第 5 节 | 物流交付：shipment、shipment_track；`GET /api/v1/orders/{orderId}/shipment`；建议 `ShipmentQueryService`；依赖订单模块真实归属查询 |
| `api/docs/architecture/module-roadmap.md` 第 145 行 | 「课程 WebService/Nginx/Redis 并发验收另行规划」——本 change 正式承接 WebService 落点（2026-11-07 团队确认落物流） |
| `db/migration/V1__legacy_schema_baseline.sql` | legacy `orders/order_detail` 是旧餐饮业务，与本模块无关；物流关联的是订单模块将新建的 `orders/order_item` |
| `db/migration/` 现状 | 已发布 V1–V5 不修改（V5 属会话模块）；本模块定编 V6/R040，订单 V7/R050、库存 V8 |
| `auth/` 包结构 | controller/service/mapper/entity/dto 三层 + 辅助包；shipment 沿用 |
| `docs/api/auth-v1.openapi.yaml` | 契约风格：IDs 字符串化、UTC 时间、统一 envelope、错误码分段 |
| `view/` 前端 | Mock 模式已具备；物流契约评审通过后即可补页面 |

## Goals / Non-Goals

**Goals：**

- 冻结物流只读查询的 REST 契约、SOAP WebService 契约与表设计，供订单负责人、前端与老师评审。
- WebService 用真实 SOAP/JAX-WS（Apache CXF）实现 WSDL 契约，满足课程「分布式框架初识」硬性要求。

**Non-Goals：**

- 不实现消费者下单、支付、物流下单/回调推送、真实承运商 SDK 对接。
- 不建 WebSocket/SSE 长连接推送（明确区分：老师要求的是 WebService，不是 WebSocket）。
- 不在本 change 写实现代码；Flyway 实际迁移在实现阶段落盘。
- 不新增物流自有错误码段（预留 12xxx）。

## 决策一：REST 契约

单操作，只读：

```
GET /api/v1/orders/{orderId}/shipment
Authorization: Bearer <access token>
```

- 消费者：仅本人订单；员工：本商户任意订单。归属校验在 Service/SQL 中按可信 Actor 限定（订单归属依赖订单模块，集成前用 fixture 订单联调，最终验收接真实授权）。
- 订单不存在、跨租户、非本人：统一 `404/10004`，不区分「不存在」与「无权」，不泄露存在性。
- 订单存在但无物流记录：返回 `200`，`data.status = "NOT_SHIPPED"`、`tracks: []`，让前端与 Agent Tool 拿到稳定形态，不用 404 表达业务态。
- `estimatedDeliveryAt` 仅当种子/数据提供时返回，不编造。
- `trackingNo` 只出现在已授权响应中；不提供任何按运单号的独立查询入口。

错误码：本期仅复用公共段（400/10001、401/10002、403/10003、404/10004、500/10500）；预留 12xxx 物流段（如将来物流数据异常/轨迹损坏）。

## 决策二：WebService（SOAP/JAX-WS）落点与信任模型

**选型**：Apache CXF `cxf-spring-boot-starter-jaxws`（4.x，Jakarta 兼容 Boot 3.5），不选 JAX-WS RI（Metro 对 Jakarta 迁移滞后）。端点类 `shipment/webservice/ShipmentWebService`，注册到 `/services/shipment`，WSDL：`GET /services/shipment?wsdl`。

**接口草案**（`ShipmentWebServicePort`，targetNamespace `https://gba.merchantbao/shipment/v1`）：

```text
String ping()
  — 连通性与课程演示用，返回固定问候串。

ShipmentSummaryDTO queryShipmentByOrderNo(String merchantCode, String orderNo)
  — 按商户编码+订单号返回物流摘要：status、carrierName、trackingNo、
    lastEventTime、tracks[]（脱敏轨迹摘要）。
  — merchantCode + orderNo 不存在 → SOAP Fault（.NOT_FOUND），不泄露跨商户数据。
```

**信任模型（必须写清的边界）**：

1. REST 端点的身份只来自认证 Actor（Bearer + Redis 会话），SOAP 端点**不接**浏览器会话。
2. SOAP 端点是**独立信任域**：服务间凭证（配置注入的 server-to-server token，随 SOAP Header 传递），定位为课程「分布式框架初识」演示 + 模拟「与外部承运商系统对接」的业务场景。
3. SOAP 与 REST 共用同一个 `ShipmentQueryService`，保证数据与脱敏规则同源；SOAP 不提供任何绕过 REST 授权的消费者级查询。
4. 服务间凭证不进入前端、不进入仓库明文配置（与 JWT 密钥同样的 .env/忽略配置管理方式）；泄露即视为越权能力，生产不开放此端点。
5. 若老师/队友质疑「为什么 SOAP 不用登录 token」：答案是 SOAP 客户端模拟的是外部系统（承运商），本来就没有浏览器会话，符合分布式系统间服务调用的真实形态；REST 的授权边界没有被绕过。

**为什么不用 WebSocket**：2026-11-07 已在团队澄清——老师硬性要求是 WebService（SOAP/WSDL，同步服务调用），不是 WebSocket 长连接推送。本模块不实现任何 WebSocket。

## 决策三：数据设计

`shipment`（一单一运单）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT UNSIGNED PK | Snowflake，字符串化输出 |
| merchant_id | BIGINT UNSIGNED NOT NULL | 租户根，所有查询必带 |
| order_id | BIGINT UNSIGNED NOT NULL | 逻辑关联订单模块 orders.id，**不建物理外键** |
| order_no | VARCHAR(64) NOT NULL | 下单时快照，SOAP 查询键之一 |
| status | VARCHAR(20) NOT NULL | `NOT_SHIPPED` / `IN_TRANSIT` / `DELIVERED` |
| carrier_name | VARCHAR(64) NOT NULL | 承运商展示名 |
| carrier_code | VARCHAR(32) NULL | 承运商编码 |
| tracking_no | VARCHAR(64) NOT NULL | 运单号 |
| estimated_delivery_at | DATETIME NULL | 仅数据提供时返回 |
| delivered_at | DATETIME NULL | 签收时间 |
| created_at / updated_at | DATETIME NOT NULL | UTC |

索引：`UNIQUE(merchant_id, order_id)`（一单一运单）、`UNIQUE(merchant_id, tracking_no)`。

`shipment_track`（轨迹事件，只追加）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT UNSIGNED PK | Snowflake |
| shipment_id | BIGINT UNSIGNED NOT NULL | 逻辑关联 shipment.id |
| merchant_id | BIGINT UNSIGNED NOT NULL | 冗余租户列，隔离查询不 join |
| event_time | DATETIME NOT NULL | 事件时间，UTC |
| status | VARCHAR(20) NOT NULL | 事件时状态 |
| description | VARCHAR(255) NOT NULL | 轨迹描述（脱敏） |
| location | VARCHAR(128) NULL | 事件地点 |

索引：`KEY(shipment_id, event_time)`、`UNIQUE(shipment_id, event_time, description)`（种子幂等）。

- 不建物理外键的原因：订单表由订单模块的迁移创建，物理顺序与 Flyway 编号必须协调；授权由 Service/SQL 强制，与 auth 的做法一致。
- devdata：`R__040_shipment_demo_data.sql` 提供未发货/多段轨迹/已签收三类演示，两租户各一份用于隔离演示；R__030 已被会话模块占用。
- test fixture：`src/test/resources/db/fixture/shipment.sql`，测试 `@Sql` 显式加载。

## Risks / Trade-offs

1. **订单模块未就绪**：归属校验先用 fixture 订单联调；最终验收必须接真实订单授权，否则模块不算完成（roadmap 明文）。
2. **Flyway 编号竞争（已关闭）**：main 合入会话模块后 V5/R030 被占用，本 change 定编 **V6/R040**，订单模块 V7/R050、库存模块 V8 排在其后。因不建物理外键，物流表先于订单表创建不构成依赖。
3. **SOAP 端点被误解为绕过授权**：信任模型已在决策二写死；评审时重点让队友与老师确认这一边界。
4. **CXF 与 Springdoc 共存**：`/services/*` 不进入 OpenAPI 文档，WSDL 独立提供；Springdoc 扫描排除 WebService 端点，避免契约混淆。
