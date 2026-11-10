# Proposal

## Why

认证已提供可信的消费者/员工 Actor 与租户解析，但商品域没有任何实现。商品模块是库存、订单、物流三个模块的上游（SKU 标识与商户归属），也是 Agent 商品 Tool 的数据前置；需要先稳定 SKU 与商品事实模型，其余模块才能并行开工。

## What Changes

1. **数据模型**：新增 `product_category`、`product`、`product_sku`、`product_attribute` 四张表（Flyway V6），全部按多商户设计：行归属 `merchant_id`，ID 为雪花号，可筛选属性（机型、接口、协议）存 `product_attribute`，SKU 组合规格（颜色、长度、功率）存 `attributes_json`，价格 `DECIMAL(12,2)` 禁用浮点。
2. **公开商城查询**：匿名 GET `/api/v1/public/stores/{merchantCode}/products`、`/products/{productId}`、`/skus/{skuId}`，只返回 `PUBLISHED` 商品与 `ACTIVE` SKU；商户按店铺编码解析且必须 `ACTIVE`。
3. **登录商品查询**：GET `/api/v1/products`、`/products/{productId}`、`/skus/{skuId}` 供任意已认证主体读取；消费者只能看到已发布内容，员工可见本商户全部状态数据。
4. **员工维护接口**：分类、商品、上下架、属性、SKU 的创建/更新，仅 `MERCHANT_STAFF`，全部限定本商户；不提供物理删除，启停/停售用状态表达。
5. **Agent 查询能力**：`ProductQueryService` 暴露按商户与可见范围查询商品/SKU/兼容性属性的 Service 方法，返回名称、规格、状态等必要字段，作为后续商品只读 Tool 的接入点。
6. **认证白名单显式开放**：`AuthSecurityFilter.anonymous()` 增加 GET `/api/v1/public/**`；非 GET 的商品域路径要求 `MERCHANT_STAFF` 角色。该开放由本 change 显式声明，不是自动生效。

本期不做：库存、下单支付、购物车、平台后台、商品图片上传、分类物理删除、面向消费者的复杂筛选聚合（首期按分类与状态列表分页）。

## Capabilities

### New Capabilities

- `product-module`: 分类/商品/SKU/属性的多租户数据模型、公开商城查询、员工维护与给 Agent 的商品事实查询。

### Modified Capabilities

无。认证仅被消费（匿名白名单开放与角色门禁），不改变其契约语义。

## Impact

- `api/src/main/resources/db/migration/V8__product_module.sql`：四张表、复合外键、状态 CHECK 与租户优先索引。
- 新增 `com.gba.merchantbao.product` 业务包（controller/service/mapper/entity/dto/vo/exception/config），沿用三层包约定；`MyBatisConfiguration` 与 Springdoc 扫描登记。
- `AuthSecurityFilter` 增加 `/api/v1/public/**` 匿名 GET 白名单与商品域写操作角色门禁。
- dev 演示数据 `R__050_product_demo.sql`：两租户、充电器/数据线/耳机及多 SKU、未发布与停售样例，幂等补缺不覆盖。
- 测试：请求校验单元测试、Mapper/Service 集成测试（Testcontainers MySQL）与 `@Sql` fixture。
- 文档：`api/docs/product/module.md` 维护文档与 OpenAPI 分组。
