# order-query Capability

## ADDED Requirements

### Requirement: 消费者本人订单只读查询

消费者（CONSUMER）可分页查询本人订单列表，并查看本人订单详情；身份范围由服务端 Actor 提供（merchantId + accountId），不得由请求参数或 Agent 模型覆盖。

#### Scenario: 消费者查看本人订单列表

- WHEN 已登录消费者 GET `/api/v1/orders`
- THEN 返回仅含本人订单的分页结果（records/total/current/size），按下单时间倒序
- THEN pageNum 默认 1，pageSize 默认 10、上限 100

#### Scenario: 消费者查看本人订单详情

- WHEN 已登录消费者 GET `/api/v1/orders/{orderId}` 且订单属于本人
- THEN 返回订单履约状态、支付状态、金额与订单项成交快照

#### Scenario: 同租户他人订单不泄露存在性

- WHEN 已登录消费者 GET `/api/v1/orders/{orderId}` 且订单属于同租户其他消费者
- THEN 返回 404/10004，与不存在的订单不可区分

### Requirement: 商户员工本商户订单查询

商户员工（MERCHANT_STAFF）可分页查询本商户全部订单并查看其中任一订单详情；不得访问其他商户数据。

#### Scenario: 员工查看本商户订单

- WHEN 已登录员工 GET `/api/v1/orders`
- THEN 返回本商户全部分页订单

#### Scenario: 跨租户订单不可见

- WHEN 员工 GET 其他商户的订单 ID
- THEN 返回 404/10004，即使该订单号与本商户某订单号相同

### Requirement: 订单项成交快照

订单项保存商品名称、SKU 属性、成交单价、数量与金额的下单时快照；历史订单展示不回联当前商品数据。

#### Scenario: 商品变更不影响历史快照

- WHEN 订单创建后商品名称、SKU 属性或价格发生变化
- THEN 订单详情仍返回下单时的快照值

### Requirement: 数据租户隔离兜底

orders/order_item 所有查询语句必带 merchant_id 与主体范围；组合外键使跨租户行引用在数据库层不可表达。

#### Scenario: 跨租户外键被拒绝

- WHEN 插入 merchant_id 与 order_id 所属租户不一致的订单项
- THEN 数据库拒绝该写入（外键约束失败）

### Requirement: 未认证与非法输入

未认证请求访问订单接口返回 401；分页参数越界或路径 ID 非法返回 400/10001。

#### Scenario: 匿名请求被拒绝

- WHEN 未携带有效 Bearer access 调用订单接口
- THEN 返回 401/10002，不返回任何订单数据

#### Scenario: pageSize 超上限

- WHEN GET `/api/v1/orders?pageSize=101`
- THEN 返回 400/10001
