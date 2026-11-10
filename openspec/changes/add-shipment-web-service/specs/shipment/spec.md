# Spec Delta

## Purpose

定义按已授权订单查询物流状态、承运商、运单与轨迹的只读行为，含 REST 入口与 SOAP WebService 端点（课程分布式框架落点）。实现已交付（2026-11-10），验收证据见 `api/docs/shipment/verification.md`，任务状态以本 change 的 `tasks.md` 为准。

## ADDED Requirements

### Requirement: Authorized principals shall query shipment of orders they can access

已认证主体 MUST 能通过 `GET /api/v1/orders/{orderId}/shipment` 查询其有权查看订单的物流：消费者仅限本人订单，员工仅限本商户订单。归属校验 MUST 由 Service 与 SQL 按可信 Actor 执行，MUST NOT 信任请求中的 merchantId/customerId/订单号自报身份。订单不存在、跨租户、非本人 MUST 统一返回 404，不区分不存在与无权，不泄露存在性。

#### Scenario: Consumer queries own shipment

- **WHEN** 消费者使用有效 access token 查询本人订单的物流
- **THEN** 返回 200，含 status、承运商、运单号与轨迹列表；IDs 为字符串、时间为 UTC

#### Scenario: Cross-tenant or foreign order is indistinguishable from missing

- **WHEN** 消费者查询他人订单、任意主体查询跨商户订单，或订单号不存在
- **THEN** 统一返回 404/10004；响应不泄露该订单是否存在物流记录

#### Scenario: Anonymous request is rejected

- **WHEN** 未携带有效凭证请求物流查询
- **THEN** 返回 401，不返回任何物流信息

### Requirement: Shipment status and tracks shall only reflect recorded data

物流状态 MUST 为 `NOT_SHIPPED`（未发货）、`IN_TRANSIT`（运输中）、`DELIVERED`（已签收）之一。订单无物流记录时 MUST 返回 200 与 `NOT_SHIPPED` 空轨迹，不用 404 表达业务态。`estimatedDeliveryAt` 仅当数据提供时返回，系统 MUST NOT 编造预计送达或轨迹事件。轨迹按 event_time 升序返回。

#### Scenario: Order without shipment record

- **WHEN** 订单归属校验通过但无 shipment 记录
- **THEN** 返回 200，status 为 NOT_SHIPPED、tracks 为空数组、无承运商与运单号

#### Scenario: Estimated delivery only when provided

- **WHEN** shipment 记录不含预计送达时间
- **THEN** 响应中该字段缺失或为 null，不生成默认时间

### Requirement: Tracking number shall not bypass order authorization

系统 MUST NOT 提供按运单号（trackingNo）绕过本人/本商户订单授权的独立查询入口；trackingNo 只出现在已授权订单的响应中。任何按运单号定位物流的内部实现 MUST 先解析订单归属再返回数据。

#### Scenario: Tracking number lookup is not a public capability

- **WHEN** 匿名或越权主体尝试用运单号获取物流
- **THEN** 无可用公开入口；私有入口按可信 Actor 授权后仍统一 404

### Requirement: SOAP WebService shall expose the same read-only shipment capability as a separate trust domain

系统 MUST 通过 Apache CXF 发布 JAX-WS 端点（WSDL 可访问），提供 `ping` 与按商户编码+订单号的物流摘要查询，内部复用与 REST 相同的 `ShipmentQueryService` 与脱敏规则。SOAP 端点 MUST 属于独立信任域：使用服务间凭证（配置注入），MUST NOT 接受浏览器 Bearer 会话，MUST NOT 为消费者提供绕过 REST 授权的查询路径。无效凭证、订单不存在或跨商户 MUST 返回 SOAP Fault，不泄露数据。该端点为课程分布式框架演示与模拟外部承运商对接，MUST NOT 开放为生产对公能力。

#### Scenario: SOAP client round trip

- **WHEN** 持有效服务间凭证的 SOAP 客户端按 WSDL 调用 queryShipmentByOrderNo
- **THEN** 返回与 REST 同源、脱敏后的物流摘要；WSDL 可通过 HTTP GET 获取且结构稳定

#### Scenario: Invalid credential or foreign order

- **WHEN** SOAP 请求凭证无效，或 merchantCode+orderNo 不存在/跨商户
- **THEN** 返回 SOAP Fault，不返回任何物流数据；REST 端点的授权行为不受 SOAP 端点引入的影响

#### Scenario: WebService is not WebSocket

- **WHEN** 评审或验收讨论课程硬性技术
- **THEN** 本模块交付的是 SOAP/WSDL 同步服务调用（WebService），不实现 WebSocket 长连接推送

### Requirement: Shipment data shall be tenant-scoped without physical foreign keys to unwritten tables

shipment、shipment_track MUST 携带 merchant_id 并在所有查询中限定租户；与订单模块的关联为逻辑关联（order_id/order_no），MUST NOT 建立指向尚未创建订单表的物理外键；物理迁移顺序 MUST 晚于订单表迁移，Flyway 编号由团队协调。dev 演示数据只进 devdata，测试数据只进 fixture，二者不混用。

#### Scenario: Migration ordering coordinated with order module

- **WHEN** 物流迁移与订单/库存模块迁移合并到同一数据库
- **THEN** 物流迁移在订单表迁移之后执行，编号经协调且不修改已发布迁移

#### Scenario: Tenant isolation in every query

- **WHEN** 任意物流查询执行
- **THEN** SQL 携带 merchant_id 限定，跨租户数据不可通过任何参数组合取得
