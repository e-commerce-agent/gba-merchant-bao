# Proposal

## Why

认证模块已交付可信 Actor（服务端注入 merchantId/role/accountId）与 `/api/v1` 安全链，订单是四个队友模块中消费者侧最先依赖的只读事实源：消费者要查本人订单与物流，Agent 订单 Tool 需要 Java Service 接入，商户工作台需要按商户查看订单。本 change 交付订单只读查询闭环，为物流模块（SKU→订单→物流依赖链最后一环）和 Agent 工具接入提供可联调的真实 Service；下单、支付、退款回补等写操作首期不做（消费者不下单，订单来自种子或后续商户后台）。

## What Changes

- 按"业务需求与验收 → API 契约 → 表/索引/Flyway → 完整后端代码 → 测试 → dev 演示数据 → 维护文档 → 导出 OpenAPI"的顺序交付 order 业务域。
- 新增 `orders` 与 `order_item` 两表：订单保存履约状态（待支付/待发货/已发货/已完成/已取消）与支付状态（未支付/已支付/退款中/已退款）；订单项保存商品名称、SKU 属性、成交单价、数量与金额的成交快照，历史展示不回联当前商品数据。
- 新增 `com.gba.merchantbao.order` 业务域，沿用 auth 的 controller/service/mapper 三层结构：`OrderController` 只做协议适配，`OrderQueryService` 承载三层授权（先角色、再租户、再资源归属），Mapper/XML 所有语句必带 `merchant_id` 与主体范围；越界与不存在统一 404/10004。
- HTTP 提供两个只读入口：`GET /api/v1/orders`（分页，pageNum 默认 1、pageSize 默认 10 上限 100）与 `GET /api/v1/orders/{orderId}`（含订单项快照）；沿用统一 envelope、字符串化 ID、UTC 时间与 no-store。
- 订单模型不保存手机号、地址等隐私字段；`OrderQueryService` 即 Agent 订单 Tool 的接入面（Java Service 注入，不走 HTTP 回调），返回同一脱敏投影。
- devdata 新增 `R__050_order_demo_orders.sql`（编号 050）：3C 样板租户覆盖全部状态的 6 单与隔离租户复用同号的 1 单；测试数据走 `@Sql` fixture，稳定 ID 20000000000003xx。
- 集成测试覆盖：Mapper/Service 租户与本人范围、真实 SQL EXPLAIN 走索引、组合外键拒绝跨租户引用、CHECK 约束拒绝非法状态、HTTP 契约（分页上限、404 不泄露存在性、匿名 401、ID 字符串化）。

## Capabilities

### New Capabilities

- `order-query`: 消费者本人订单只读列表/详情、员工本商户订单查询、成交快照不可变性、越界与不存在同为 404 的三层授权边界。

### Modified Capabilities

无。现有 `user-auth` 的 Actor 与安全链、`application-technical-baseline` 的迁移/契约要求继续适用；本 change 不改写既有能力。

## Impact

- 后端 `api/`：新增 `order` 业务域（controller/service/mapper/entity/dto/exception/handler/config）、`V7__order_tables.sql` 增量迁移、Mapper XML 与 MyBatis 扫描扩展。
- API：新增 `/api/v1/orders` 与 `/api/v1/orders/{orderId}` 两个受保护 GET；springdoc 新分组 `order-v1` 供前端导出 OpenAPI。
- 数据：devdata 增加 030 号种子；测试 fixture 增加 `orders.sql`；新 V 不依赖 R 数据。
- Agent：`OrderQueryService` 就绪后即可接入订单 Tool，不阻塞物流模块并行开发；最终字段/索引在本模块 change 冻结。
- 不做：下单/支付/取消/退款写操作、库存扣减、支付回调、订单号生成规则对外的写语义、平台管理员视角。
