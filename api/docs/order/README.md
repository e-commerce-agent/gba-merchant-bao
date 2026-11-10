# 订单只读查询模块（order）

本模块交付订单查询闭环：`trade_order`/`trade_order_item` 两表（`orders` 已被 V1 legacy 基线占用）与索引、只读 Mapper/XML、`OrderQueryService` 授权查询、`/api/v1/orders` 两个 HTTP 入口、dev 演示订单与集成测试。首期消费者不下单（见 `docs/initialization/设计手稿.md` 与角色矩阵），订单数据来自本模块 dev 种子或后续商户后台写入；下单、支付、扣库存与支付回调不属于本 change。

## 需求与验收

- 消费者：只读本人订单列表与详情；身份来自认证 Actor（`merchantId + accountId`），不能由请求参数或 Agent 模型覆盖。
- 商户员工：只读本商户全部订单（工作台用）；跨商户数据不可见。
- 越界（同租户他人订单 / 跨租户 / 不存在）统一 404/10004，不泄露资源存在性。
- 订单项保存成交快照：商品名称、SKU 属性、成交单价、数量与金额；历史订单展示不回联当前商品数据，商品后续变更不影响快照。
- 订单模型不保存手机号、地址等隐私字段；Agent `OrderQueryService` 复用同一投影，天然满足"隐私不进 Tool 返回"。
- 分页遵循第 6 章规范：`pageNum` 默认 1，`pageSize` 默认 10、上限 100，响应含 `records/total/current/size`。

## API 契约（/api/v1/orders）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/orders?pageNum=&pageSize=` | 分页列表：消费者本人订单、员工本商户订单 |
| GET | `/api/v1/orders/{orderId}` | 详情含订单项快照 |

- 统一 `ApiResponse` envelope（`code/message/data/requestId`），ID 以字符串输出（`ApiId`），时间为 UTC。
- 受保护接口：`/api/v1/**` v1 安全链校验 Bearer access；成功私有操作后 `sessions.touch` 推进 idle。
- 错误：400/10001（分页参数无效、路径 ID 非法）、401/10002、404/10004、500/10500。
- OpenAPI：springdoc 分组 `order-v1`（`OrderOpenApiConfiguration`），联调时启动应用访问 `/v3/api-docs/order-v1` 供前端导出。

## 数据设计

`V7__order_tables.sql`（冻结字段以迁移脚本为准）：

- `trade_order`：`id`（Snowflake）、`merchant_id`、`order_no`（ASCII）、`customer_id`（= `consumer_account.id`）、`status`（`PENDING_PAYMENT/PENDING_SHIPMENT/SHIPPED/COMPLETED/CANCELLED`）、`pay_status`（`UNPAID/PAID/REFUNDING/REFUNDED`）、`total_amount`、`paid_amount`（可空，未支付为 NULL）、`currency`（默认 CNY）、`placed_at`、`created_at/updated_at/version`。
- `trade_order_item`：`id`、`merchant_id`、`order_id`、`product_id/sku_id`（仅溯源引用，不设外键到商品表，商品表由商品模块交付）、`product_name_snapshot`、`sku_attrs_snapshot`（JSON 文本）、`unit_price`、`quantity`、`amount`。
- 索引：`(merchant_id, order_no)` 唯一（订单号租户内唯一，也支撑隔离租户复用同号）；`(merchant_id, id)` 唯一（资源定位 + 组合外键目标）；`(merchant_id, customer_id, placed_at)` 支撑本人订单列表；`(merchant_id, order_id)` 支撑订单项装载。
- 组合外键 `(merchant_id, order_id)`、`(merchant_id, customer_id)` 在数据库层兜底跨租户引用，CHECK 约束限定状态与金额非负。

## 演示数据

- devdata：`R__050_order_demo_orders.sql`（编号 050 已按 devdata README 登记）。样板 3C 租户 6 单（覆盖全部履约状态与退款态，含一名第二名消费者订单），隔离租户 1 单（复用同号 DEV-2026-0001，验证租户隔离）。仅在缺失时插入，稳定 ID 21000000000003xx。
- 测试：`src/test/resources/db/fixture/orders.sql`（稳定 ID 20000000000003xx），由 `@Sql` 显式加载，依赖 `auth_accounts.sql` 的租户/消费者/员工。

## 测试

- `OrderPersistenceIT`：Mapper 租户/本人范围、Service 授权与 404、分页、真实 SQL `EXPLAIN` 走索引、组合外键拒绝跨租户、CHECK 约束拒绝非法状态。
- `OrderFlowIT`：HTTP 契约——envelope/requestId、ID 字符串化、分页默认与上限、消费者本人列表/详情快照、员工商户范围、跨租户/他人/不存在均 404/10004、匿名 401、pageSize>100 与非法 pageNum 400/10001。
- 运行：`mvn -f api/pom.xml clean verify`（需 Docker，集成测试不可跳过）。

## 维护约定

- 本模块只读：新增写操作（下单/取消/退款回补）另开 change，不得在本包内追加写 Mapper。
- Controller 不直接访问 Mapper；授权与范围收敛在 `OrderQueryService`；SQL 必带 `merchant_id` 与主体范围。
- Agent Function Calling 直接注入 `OrderQueryService`（Java Service，不走 HTTP 回调），返回同一投影。
