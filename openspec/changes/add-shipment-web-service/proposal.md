# Proposal

> 状态：已实现（2026-11-10）。本 change 冻结需求、API/SOAP 契约与表设计，并已落地为代码、迁移与测试；验收证据见 `tasks.md` 与 `api/docs/shipment/verification.md`。未合并 main、未发布生产。

## Why

物流模块（shipment）由本人在 module-roadmap 第 5 节认领：基于用户有权查看的订单，返回是否发货、承运商、运单与物流轨迹。订单与授权由订单模块提供，目前尚未实现；按 roadmap「物流负责人可提前实现自己的表/查询代码，最终验收须接真实订单授权」的并行规则，本 change 先交付可评审的契约层，让订单负责人与前端尽早对齐，同时承接老师硬性要求中「WebService（分布式框架初识）」的落点——团队已于 2026-11-07 确认：要实现的是 WebService（SOAP/JAX-WS），不是 WebSocket，且落点定在物流模块。

## What Changes

- 定义物流只读查询域：按订单查询发货状态（未发货/运输中/已签收）、承运商、运单号与轨迹；只有数据提供时才返回预计送达，不编造时间。
- 新增 REST 契约：`GET /api/v1/orders/{orderId}/shipment`，Bearer access 认证；消费者限本人订单，员工限本商户订单；越权与不存在统一 404，不泄露存在性。
- 新增 SOAP WebService 契约（课程分布式框架落点）：Apache CXF（JAX-WS，Jakarta 兼容）发布 `ShipmentWebService` 端点，WSDL 可访问；操作为按商户编码+订单号查询物流摘要，返回与 REST 同源、脱敏的 `ShipmentQueryService` 结果。SOAP 端点是独立信任域（服务间凭证），定位为「模拟与外部承运商系统对接」的课程演示，不替代 REST 授权，不暴露给浏览器前端。
- 新增数据设计：`shipment`、`shipment_track` 两表，商户租户隔离，逻辑关联订单（不建物理外键，订单表由订单模块后续迁移创建，物理顺序与 Flyway 编号需团队协调）。
- 错误码：物流模块本期不新增自有错误码段，复用公共段（400/10001、401/10002、403/10003、404/10004、500/10500）；预留 12xxx 段备将来物流数据异常使用。
- 前端影响：REST 契约评审通过后，前端可在 Mock 模式按本契约补物流查询页面（订单详情内嵌物流轨迹）。

## Capabilities

### New Capabilities

- `shipment-query`: 按已授权订单查询物流状态、承运商、运单与轨迹的只读查询能力，含 REST 入口、SOAP WebService 端点（课程分布式框架落点）与租户隔离。

### Modified Capabilities

无。三层授权、统一 envelope、ID 字符串化、UTC 时间等既有约定继续适用；不修改认证、订单、商品、库存的任何行为。

## Impact

- 后端 `api/`（已落地）：新增 `shipment/` 业务域（controller/service/mapper/entity/dto 三层结构），新增 CXF 依赖与 `/services/*` 端点注册，新增 `V6__shipment_tables.sql`（编号已定稿：V5/R030 已被会话模块占用，故物流取 V6/R040，订单 V7/R050、库存 V8）、devdata 与 test fixture。
- API：新增一个 REST 操作与一个 SOAP 端点；不改动已有 `/api/v1/**` 行为、不新增匿名入口。
- 授权：物流查询复用可信 Actor；订单归属验证依赖订单模块的真实查询，集成前以 fixture 订单数据联调，最终验收必须接真实订单授权。
- 文档：`api/docs/shipment/`（requirements / api-design / data-design / manual-verification / verification），`docs/api/shipment-v1.openapi.yaml` 已由实际 Springdoc `shipment-v1` 分组导出并替换评审稿。
- 验收：未发货、多段轨迹、已签收、越权 404、trackingNo 不可绕过授权、WSDL 可访问且 SOAP 客户端往返成功。
