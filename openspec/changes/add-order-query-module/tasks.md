# Tasks

## 1. 业务需求与 API 契约

- [x] 1.1 依据需求文档第 2/3/5/6/9 章、设计手稿 1.1.4 与角色-路由-资源矩阵，冻结订单只读范围：消费者本人订单、员工本商户订单、越界与不存在同为 404、成交快照不回联商品、分页规范（pageNum 默认 1、pageSize 默认 10 上限 100）。
- [x] 1.2 登记 API 契约：`GET /api/v1/orders`、`GET /api/v1/orders/{orderId}`、envelope/字符串 ID/UTC/错误码（400/10001、401/10002、404/10004、500/10500），springdoc 分组 `order-v1`。

## 2. 表结构、索引与 Flyway

- [x] 2.1 新增 `V7__order_tables.sql`：orders/order_item 两表，租户内订单号唯一、`(merchant_id,id)` 组合键、本人订单列表索引、订单项装载索引、组合外键与 CHECK 约束；新 V 不依赖 R 数据。
- [x] 2.2 devdata `R__050_order_demo_orders.sql`（编号 050 登记）：3C 租户 6 单覆盖全部履约/支付状态、隔离租户复用同号 1 单；幂等补缺失，稳定 ID 21000000000003xx；测试 fixture `db/fixture/orders.sql` 稳定 ID 20000000000003xx 由 `@Sql` 加载。

## 3. 后端代码

- [x] 3.1 `order/mapper` + XML：findById / findOwnedById / findByCustomer / countByCustomer / findByMerchant / countByMerchant / OrderItemMapper.findByOrder，语句必带 merchant_id 与主体范围；MapperScan 扩展 `order.mapper`。
- [x] 3.2 `OrderQueryService`：三层授权（先角色、再租户、再本人/商户范围），404 不泄露存在性；投影 record（Summary/ItemView/Detail/Page）复用为 Agent Tool 接入面。
- [x] 3.3 `OrderController`（GET 列表/详情、`@AuthenticationPrincipal Actor`、成功后 `sessions.touch`）、`OrderListQuery` 校验、`OrderError`/`OrderHttpErrors`/`OrderExceptionHandler`（400/10001、404/10004、500/10500）、`OrderOpenApiConfiguration` 分组。

## 4. 测试与验收

- [x] 4.1 `OrderPersistenceIT`：租户/本人范围、同号跨租户不泄露、Service 授权与 404、分页 offset 边界、真实 SQL EXPLAIN 走索引、组合外键拒绝跨租户、CHECK 拒绝非法状态。
- [x] 4.2 `OrderFlowIT`：HTTP 契约——envelope/requestId、ID 字符串化、分页默认/上限/非法值 400、消费者列表与详情快照、员工商户范围、跨租户/他人/不存在 404/10004、匿名 401。
- [x] 4.3 在具备 Docker 的开发机执行 `mvn -f api/pom.xml clean verify`，确认全量既有测试与新增 IT 通过。**结果：BUILD SUCCESS，单元 78/78 + 集成 115/115（2026-11-10，合并物流分支后重跑；首次全量时 ChatHttpIT 断连用例的 5 秒等待偶发超时，单类重跑与二次全量均通过，与本模块改动无关）。**

## 5. 文档与联调

- [x] 5.1 交付 `api/docs/order/README.md` 维护文档（需求/API/数据/演示数据/测试/维护约定）。
- [ ] 5.2 团队联调：启动应用导出 `order-v1` OpenAPI 交前端与 Agent 侧；与物流模块核对 SKU→订单→物流依赖接口就绪后接入其 Tool。
