# 物流模块需求与验收

> 状态：已实现（联调态，2026-11-10），未合并 main、未发布生产。依据 `api/docs/architecture/module-roadmap.md` 第 5 节与 `openspec/changes/add-shipment-web-service/`；验收证据见 [verification.md](verification.md)，联调步骤见 [manual-verification.md](manual-verification.md)。

## 1. 模块定位

物流模块是**只读查询模块**：基于用户有权查看的订单，返回是否发货、承运商、运单与物流轨迹。负责人：冯铿铭。同时承接课程硬性要求中的 **WebService（分布式框架初识）** 落点（团队 2026-11-07 确认：实现的是 WebService/SOAP，不是 WebSocket；落点定在物流模块）。

## 2. 功能需求

| 编号 | 需求 | 说明 |
| --- | --- | --- |
| S-1 | 按订单查询物流 | 已授权主体查询单个订单的物流状态、承运商、运单号与轨迹 |
| S-2 | 三态收口 | `NOT_SHIPPED` 未发货 / `IN_TRANSIT` 运输中 / `DELIVERED` 已签收；无物流记录视作未发货 |
| S-3 | 不编造数据 | `estimatedDeliveryAt` 仅数据提供时返回；轨迹只来自 shipment_track 记录 |
| S-4 | 越权不可区分 | 订单不存在、跨租户、非本人统一 404，不泄露存在性 |
| S-5 | 运单号不可绕过 | 不提供按 trackingNo 的独立查询入口；运单号只在已授权响应中出现 |
| S-6 | WebService 端点 | SOAP/JAX-WS（Apache CXF）发布物流查询端点，WSDL 可访问，与 REST 同源同脱敏 |
| S-7 | 租户隔离 | shipment/shipment_track 全查询带 merchant_id；与订单模块逻辑关联、不建指向未建表的物理外键 |

## 3. 主体与授权

| 主体 | 可查询范围 | 身份来源 |
| --- | --- | --- |
| 消费者 CONSUMER | 本人订单 | 认证 Actor（Bearer + Redis 会话） |
| 员工 MERCHANT_STAFF | 本商户订单 | 认证 Actor |
| SOAP 客户端（服务间） | 按 merchantCode+orderNo，独立信任域 | 服务间凭证（配置注入，随 SOAP Header） |

- 订单归属校验依赖订单模块的真实查询：集成前用 test fixture 订单数据联调，**最终验收必须接真实订单授权**（roadmap 明文）。
- 请求中出现 customerId/merchantId/订单号仅作定位参数，不能替代授权。

## 4. WebService 边界（与 WebSocket 的区分）

- 老师硬性要求是 **WebService**（SOAP/WSDL 同步服务调用，分布式框架初识），**不是 WebSocket** 长连接推送；本模块不实现任何 WebSocket。
- SOAP 端点定位：课程演示 + 模拟「与外部承运商系统对接」；独立信任域，不接受浏览器 Bearer 会话，不提供绕过 REST 授权的消费者级查询。
- 服务间凭证不进前端、不进仓库明文配置；泄露视为越权能力；生产不开放此端点。

## 5. 验收标准（实现阶段逐条勾验）

- [ ] 未发货订单：200 / NOT_SHIPPED / 空轨迹 / 无承运商运单。
- [ ] 多段运输轨迹：IN_TRANSIT，轨迹按 event_time 升序完整返回。
- [ ] 已签收：DELIVERED + deliveredAt。
- [ ] 消费者查本人订单 200；员工查同商户订单 200。
- [ ] 消费者查他人订单 / 跨租户订单 / 不存在订单：统一 404，响应形态一致。
- [ ] 无任何公开按运单号查询入口；私有入口授权后仍统一 404。
- [ ] WSDL 通过 HTTP GET 可访问；CXF 客户端往返成功；无效凭证返回 Fault。
- [ ] REST 授权行为不受 SOAP 端点引入影响；V1–V4 校验和不变。
- [ ] 两租户隔离演示数据齐备；test/prod 不加载 devdata。

## 6. 明确不做

消费者下单/支付、物流下单、承运商回调与推送、真实物流平台 SDK、WebSocket/SSE、物流管理后台（录运单）——运单数据由 devdata 种子提供。
