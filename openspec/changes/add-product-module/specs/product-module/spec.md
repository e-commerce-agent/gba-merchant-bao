# Spec Delta

## Purpose

提供多商户电商的商品事实模型与查询能力：管理 3C 商品及其可售规格，为公开商城、登录用户和后续 Agent 商品 Tool 提供按商户与可见范围过滤的商品/SKU/兼容性属性数据。库存、订单、物流依赖本能力冻结的 SKU 标识与商户归属。

## ADDED Requirements

### Requirement: Product data shall be tenant-scoped and multi-merchant by design

系统 MUST 以 `product_category`、`product`、`product_sku`、`product_attribute` 四张表维护分类、商品、SKU 与结构化属性；每行 MUST 归属唯一 `merchant_id`，商品编码在商户内唯一，SKU 编码在商户内唯一。可筛选属性 MUST 存 `product_attribute`，SKU 组合规格 MUST 存 `attributes_json`；价格 MUST 使用定点小数且非负。已发布迁移与已冻结编码 MUST NOT 随下游模块需求变更。

#### Scenario: A merchant maintains its own catalog
- **WHEN** 员工创建分类、商品、SKU 与属性并相互关联
- **THEN** 所有行归属当前登录商户；同商户重复商品编码或 SKU 编码返回 409，另一商户使用相同编码成功

#### Scenario: Category depth is limited to two levels
- **WHEN** 员工把一个已有父分类的分类再设为父级
- **THEN** 服务拒绝该操作并返回 409；二级分类挂在根分类下成功

### Requirement: Public mall queries shall expose only published content anonymously

未登录访问者 MUST 能按店铺编码匿名分页浏览 `PUBLISHED` 商品及其 `ACTIVE` SKU；店铺编码 MUST 解析为 `ACTIVE` 商户。`DRAFT`、`UNPUBLISHED`、`DISCONTINUED` 内容与商户内部数据 MUST NOT 出现在任何公开响应中；越界与不存在 MUST 返回相同 404，不泄露存在性。

#### Scenario: Anonymous visitor browses the demo store
- **WHEN** 未登录请求 `GET /api/v1/public/stores/M_3C_DEMO/products`
- **THEN** 响应仅包含 PUBLISHED 商品分页；未发布与停售商品不可见，游标分页结果稳定

#### Scenario: Unknown or inactive store code
- **WHEN** 未登录访问不存在的店铺编码或非 ACTIVE 商户
- **THEN** 服务返回 404，响应不区分两种原因

#### Scenario: Unpublished product is invisible publicly
- **WHEN** 未登录访问某商户 UNPUBLISHED 商品的公开详情或公开 SKU
- **THEN** 服务返回 404；同一 ID 对本商户员工登录可见

### Requirement: Authenticated reads shall follow role-based visibility

已认证消费者与员工 MUST 能通过 `/api/v1/products`、`/api/v1/products/{productId}`、`/api/v1/skus/{skuId}` 读取本商户商品数据；消费者 MUST 只见 PUBLISHED/ACTIVE 内容，员工 MUST 见本商户全部状态的管理视图。分类管理视图（含 INACTIVE）MUST 仅对员工开放。每条查询 MUST 由服务端注入 `Actor` 商户范围，请求参数不能覆盖归属。

#### Scenario: Consumer reads own merchant catalog
- **WHEN** 消费者分页读取 `/api/v1/products`
- **THEN** 仅返回本商户 PUBLISHED 商品；跨商户 ID 返回 404

#### Scenario: Staff reads management view
- **WHEN** 员工读取 `/api/v1/product-categories` 或 `/api/v1/products`
- **THEN** 返回本商户含 INACTIVE 分类与非公开商品在内的管理数据，不含其他商户数据

### Requirement: Only merchant staff shall write catalog data

分类、商品、上下架、属性与 SKU 的写操作 MUST 仅允许 `MERCHANT_STAFF`；角色校验 MUST 先于请求体反序列化。上下架 MUST 只在合法状态间流转（如 `DRAFT→PUBLISHED→UNPUBLISHED`），属性 MUST 整组替换且不产生孤儿数据。本期能力 MUST NOT 包含物理删除、库存变更或下单链路。

#### Scenario: Consumer attempts a write
- **WHEN** 消费者对 `/api/v1/products` 发起 POST/PUT
- **THEN** 安全层返回 403，请求体不被处理

#### Scenario: Staff publishes a draft product
- **WHEN** 员工对 DRAFT 商品执行 publish，随后执行 unpublish
- **THEN** 商品状态变为 PUBLISHED 后变为 UNPUBLISHED；对已停售商品 publish 返回 409

### Requirement: Agent tooling shall query products through the trusted service

`ProductQueryService` MUST 提供按解析后的商户上下文查询商品、SKU 与兼容性属性的只读方法，返回名称、规格、状态等必要字段；MUST NOT 接受模型或请求自报的 merchantId/productId 作为授权依据，隐私与内部管理字段 MUST NOT 进入返回结果。

#### Scenario: Agent queries product facts
- **WHEN** 后续商品 Tool 以可信商户上下文调用 `ProductQueryService`
- **THEN** 返回该商户可见范围内的商品名称、SKU 规格与兼容性属性；无可见记录时返回空或不存在，不泄露其他商户数据

## Requirement: Public catalog reads are cached with merchant-scoped invalidation

公开商城的商品列表、商品详情与 SKU 详情 SHALL 在启用缓存时优先读取 Redis；缓存未命中或 Redis 不可用时 MUST 回源数据库并正常返回，不得因缓存故障产生 5xx。任何商品域写操作成功后 MUST 使该商户所有公开缓存条目失效。已登录读与 Agent 事实查询 MUST 不读缓存。

#### Scenario: 写操作后公开详情立即反映新状态
- **WHEN** 员工发布商品后立即匿名请求该商品详情
- **THEN** 返回 PUBLISHED 状态的最新内容（版本号已自增，旧缓存不可达）

#### Scenario: Redis 不可用
- **WHEN** Redis 连接失败时匿名请求公开列表
- **THEN** 返回数据库查询结果与 200，日志记录降级，无异常抛出
