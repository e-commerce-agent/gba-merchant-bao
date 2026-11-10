# Tasks

> 状态：全部完成（2026-11-10），并已合并 main 最新基线（会话模块 V5/R030 占用后本模块顺延为 V6/R040）；`mvn -f api/pom.xml clean verify` BUILD SUCCESS（单元 78/78 + 集成 106/106，Testcontainers MySQL/Redis），证据见 `api/docs/shipment/verification.md`。执行顺序按「契约评审 → 表 → Service/REST → WebService → 测试 → 文档验收」。

## 1. 需求与契约评审

- [x] 1.1 评审并按反馈修订 `api/docs/shipment/requirements.md`：冻结未发货/运输中/已签收三态、不编造预计送达、越权统一 404、trackingNo 不可绕过授权等行为。
- [x] 1.2 评审并按反馈修订 `api/docs/shipment/api-design.md`（REST + SOAP 双契约）与 `docs/api/shipment-v1.openapi.yaml` 评审稿；确认错误码只用公共段、预留 12xxx。
- [x] 1.3 与订单负责人对齐订单表名/主键/`order_no` 快照与 `GET /api/v1/orders/{orderId}` 授权语义；与库存负责人协调 Flyway 编号顺序。结论：**最终编号物流 V6/R040、订单 V7/R050、库存 V8**——原定 V5/V6/V7 在 main 合入会话模块后整体顺延一位（V5/R030 已被占用）；订单表因 V1 legacy 基线已占用 `orders`，落地为 `trade_order`/`trade_order_item`。
- [x] 1.4 与老师/队友确认 WebService 信任模型（SOAP 独立信任域、服务间凭证、不绕过 REST 授权）与「WebService ≠ WebSocket」的口径。

## 2. Flyway 表结构、索引与数据边界

- [x] 2.1 落盘 `V6__shipment_tables.sql`（编号已按 1.3 定稿为 V6）：shipment、shipment_track、索引、UTC 时间；不建物理外键；约束由 `ShipmentSchemaIT` 验证。
- [x] 2.2 devdata `R__040_shipment_demo_data.sql`（编号已在 db/devdata/README.md 登记）：两租户运输中/已签收演示数据（NOT_SHIPPED 由服务合成不落行），只补缺失、幂等；test/prod 不加载。
- [x] 2.3 test fixture `db/fixture/shipment.sql`：跨租户对照、越权场景、无物流记录订单；由 `@Sql` 显式加载。

## 3. ShipmentQueryService 与 REST

- [x] 3.1 实现 `ShipmentQueryService`：按可信 Actor（消费者本人/员工同商户）+ 订单归属校验查询；SQL 层带 merchant_id 限定；订单不存在/越权统一 404/10004。授权端口 `OrderAccessGuard` 已在订单模块落地后替换为真实实现 OrderBackedOrderAccessGuard（联调态实现 TenantScopedOrderAccessGuard 已删除）。
- [x] 3.2 实现 `GET /api/v1/orders/{orderId}/shipment` Controller 与严格 DTO；ID 字符串化、UTC 时间、no-store、统一 envelope；订单未发货态返回 `NOT_SHIPPED` + 空轨迹（依赖真实 OrderAccessGuard）。
- [x] 3.3 HTTP 测试覆盖：消费者本人 200、员工同商户 200、跨租户/非本人/不存在统一 404、匿名 401、无物流记录（联调态为 404，真实 guard 落地后 200/NOT_SHIPPED）、预计送达缺失不返回。（`ShipmentFlowIT`，待 Docker 运行）

## 4. WebService（SOAP/JAX-WS）端点

- [x] 4.1 引入 Apache CXF starter（4.1.3，Jakarta/Boot 3.x），注册 `/services/shipment` 端点，发布 `ShipmentWebServicePort`（ping + queryShipmentByOrderNo），WSDL 可访问；`/services/**` 不在 Springdoc 扫描范围。
- [x] 4.2 实现服务间凭证校验（CXF in-interceptor 读 SOAP Header token，配置注入、.env/忽略文件管理），无效凭证统一 SOAP Fault，不泄露数据；REST 授权路径不受影响。
- [x] 4.3 SOAP 端点内部复用 `ShipmentQueryService`；`merchantCode+orderNo` 不存在/跨商户 → SOAP Fault `NOT_FOUND`；返回与 REST 同源脱敏字段。
- [x] 4.4 测试：凭证拦截器单元测试（`ShipmentServiceTokenInterceptorTest`）通过；CXF 往返/WSDL/Fault 场景由 `ShipmentSoapIT` 覆盖（待 Docker 运行）。

## 5. 文档与验收

- [x] 5.1 交付 `api/docs/shipment/data-design.md`（表/字段/索引/编号协调记录）与 `manual-verification.md`（dev 联调步骤：两类主体查询、越权、SOAP 客户端演示）。
- [x] 5.2 实现完成后以 Springdoc 导出替换 `docs/api/shipment-v1.openapi.yaml` 评审稿，逐操作核对；更新 README 导航与 Apifox 记录。**结果：`ShipmentFlowIT.exportsActualSpringdocContractForShipmentV1` 从隔离后端实际导出 `shipment-v1` 分组并落盘（1 操作 `getOrderShipment`、bearerAuth、错误 envelope、`no-store`），快照为原始输出未手工改写；`api/README.md` 补齐物流文档与快照导航；差异与限制见 `api/docs/shipment/verification.md`。**
- [x] 5.3 运行 `mvn -f api/pom.xml test` 与 `clean verify`：HTTP/MySQL/Flyway/SOAP 集成测试真实执行，Docker 不可用明确失败，报告仅写 target。**结果：BUILD SUCCESS，单元 78/78 + 集成 106/106（2026-11-10 13:04）。**
- [x] 5.4 `openspec validate add-shipment-web-service --strict`；核对 proposal/design/tasks/specs 与 api/docs 一致；汇总已知限制（订单真实授权接入前为联调态）。**结果：`npx @fission-ai/openspec@1.17.0 validate add-shipment-web-service --strict` 通过；proposal/design/specs 的「未实现」状态已改为已实现并联到 `verification.md`；编号结论统一 V6/R040 + 订单 V7/R050 + 库存 V8；已知限制见 `verification.md`。**
