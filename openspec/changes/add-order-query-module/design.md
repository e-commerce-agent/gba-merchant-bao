# Design

## 上下文

认证 change 已冻结可信身份边界：`/api/v1/**` stateless 安全链构造不可变 `Actor`（merchantId/role/accountId 来自服务端会话），后续领域入口不得从 body/header 自报身份。订单模块是首个消费 Actor 的业务域，三层授权模式（先角色、再带范围查资源、越界和不存在同为 404）在此固化为可复制模板。

## 目标与非目标

- 目标：只读订单事实源；订单项成交快照；消费者/员工/Agent 三类调用方共用一个 Service。
- 非目标：下单/支付/取消/退款写操作、库存扣减、支付回调、运单模型（物流模块）、平台管理员。

## 关键决策

1. **customer_id = consumer_account.id**：消费者账号即订单主体，不单独建 customer 表；本人范围在 SQL 层携带 `merchant_id + customer_id`，与 `Actor.accountId` 绑定。
2. **组合键外键兜底**：`(merchant_id, order_id)`、`(merchant_id, customer_id)` 组合外键使跨租户行引用在数据库层不可表达，是应用层范围校验之外的兜底，也是隔离租户复用同一 order_no/id 的前提（`(merchant_id, order_no)`、`(merchant_id, id)` 均为租户内唯一）。
3. **快照自包含**：`order_item` 保存名称/属性/单价/数量/金额，`product_id/sku_id` 仅溯源不设商品外键（商品表由商品模块并行交付，迁移顺序解耦）；历史展示不回联当前商品，满足"商品变更不影响成交快照"验收。
4. **分页手写 LIMIT/OFFSET + COUNT**：不引入 PageHelper 插件行为，SQL 显式可 EXPLAIN（`idx_orders_merchant_customer_placed` ref 扫描），与 auth 的真实索引断言风格一致。
5. **一个 Service 三类调用方**：`OrderQueryService` 同时服务 Controller（HTTP）与后续 Agent Function Calling（Java 注入）；投影 record 不含隐私字段（订单模型本身无手机号/地址），Agent Tool 无需二次脱敏。
6. **错误边界独立**：`OrderError`/`OrderHttpErrors` 与 auth 平行，不复用 auth 异常类型；advice 只绑定 order.controller，避免吞并其他域异常。角色不是 CONSUMER/MERCHANT_STAFF 的请求直接 404，未来新增角色不误读订单。

## 风险与权衡

- 首期订单数据仅来自 dev 种子（或后续商户后台写入），"下单正确性/超卖"验收不在本模块，写路径另开 change。
- LIMIT/OFFSET 深分页性能由 `(merchant_id, customer_id, placed_at)` 索引 + 上限 100 控制，演示规模下足够；如后续运营报表需深翻页，再评估游标分页。
- `sessions.touch` 在控制器成功分支调用，与 auth 模块现有约定一致（成功私有操作推进 idle）。

## 迁移与冻结

- `V7__order_tables.sql` 为冻结契约：后续字段变更走更高版本增量，不改写 V7。
- devdata 030 号依赖 010/020 号种子（租户、消费者），locations 顺序由 README 登记约定表达，不使用 AFTER_MIGRATE 回调。
