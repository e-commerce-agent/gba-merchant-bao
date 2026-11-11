# 商品模块（product）

## 范围

管理 3C 商品及其可售规格：分类（两级）、商品/SPU、SKU、结构化兼容性属性；提供公开商城查询、登录读取与商户员工维护接口，并向后续 Agent 商品 Tool 输出 `ProductQueryService` 只读事实。库存、订单、物流依赖本模块冻结的 SKU 标识与商户归属。

- change：`openspec/changes/add-product-module/`
- 缓存：`product/cache/ProductCacheStore`（Redis，公开读路径；写操作 O(1) 版本失效）
- 迁移：`db/migration/V6__product_module.sql`（V1–V5 冻结）
- dev 演示数据：`db/devdata/R__040_product_demo.sql`（仅 dev；依赖 R010 租户）
- 测试 fixture：`src/test/resources/db/fixture/product_catalog.sql`（`@Sql` 显式加载）

## 数据模型

| 表 | 说明 | 关键约束 |
| --- | --- | --- |
| product_category | 两级分类，`parent_id` 空为根 | `(merchant_id,parent_id,name)` 唯一；父分类必须是根（Service 校验）；INACTIVE 前提是挂载商品均非在售 |
| product | 商品/SPU，状态 DRAFT/PUBLISHED/UNPUBLISHED/DISCONTINUED | `(merchant_id,product_code)` 唯一 |
| product_sku | SKU，状态 ACTIVE/DISCONTINUED | `(merchant_id,sku_code)` 唯一；`price DECIMAL(12,2) >= 0`；组合规格存 `attributes_json` |
| product_attribute | 可筛选/兼容性属性，一属性名一行，多值分号分隔 | `(merchant_id,product_id,attr_name)` 唯一 |

V7 库存及 V8 订单项通过同商户复合外键引用 SKU；订单项外键还保证 SKU 属于所记录的 product_id。复合外键不替代领域服务的状态、权限及商户归属校验，历史订单展示只读取持久化成交快照。外键限制父记录删除，不级联删除成交快照或库存流水。

规则：结构化筛选属性存 `product_attribute`；SKU 展示组合存 `attributes_json`；不建专用列，不把全部属性塞 JSON；价格定点小数禁浮点；停售商品自动停售其全部 ACTIVE SKU；停售 SKU 不可恢复销售。

## Redis 缓存（高并发主落点）

公开商城读路径是本模块的高并发落点，缓存只在 product 域内，不改动 auth 任何逻辑：

- 缓存对象：公开商品详情、公开 SKU 详情、无 cursor 的公开首页（按 store+category）；`merchantCode → merchantId` 映射另缓存 60 秒
- 键规范：条目 `gba:product:e:{merchantId}:v{ver}:{kind}:{id}`，版本 `gba:product:ver:{merchantId}`，映射 `gba:product:store:{code}`
- 失效：任何商户写操作（分类增改、商品增改、上下架、停售、属性替换、SKU 增改）`INCR` 该商户版本号，旧条目自然失效（TTL 15 分钟兜底），无需遍历删除
- 降级：Redis 不可用/超时/JSON 损坏一律当作未命中回源数据库，缓存永不成为依赖；`merchant.product.cache-enabled=false` 可整体关闭（测试环境已关闭）
- 一致性边界：缓存只覆盖匿名公开读；员工/消费者已登录读、Agent 事实查询始终直读数据库，保证管理端与 Agent 看到最新状态

## 接口契约

公开（匿名 GET，店铺编码解析 ACTIVE 商户，失败 404）：

- `GET /api/v1/public/stores/{merchantCode}/products`：PUBLISHED 分页；`size` 1–100 默认 20；`cursor` 不透明游标
- `GET /api/v1/public/stores/{merchantCode}/products/{productId}`：详情（含 ACTIVE SKU 与属性）
- `GET /api/v1/public/stores/{merchantCode}/skus/{skuId}`

登录读取（Bearer）：`GET /api/v1/products`、`GET /api/v1/products/{productId}`、`GET /api/v1/skus/{skuId}`、`GET /api/v1/product-categories`（员工管理视图，含 INACTIVE）。消费者只见 PUBLISHED/ACTIVE，员工见本商户全部状态。

员工维护（MERCHANT_STAFF，非 GET 由 `AuthSecurityFilter` 在反序列化前门禁）：

- `POST /api/v1/product-categories`、`PUT /api/v1/product-categories/{categoryId}`
- `POST /api/v1/products`、`PUT /api/v1/products/{productId}`、`POST /api/v1/products/{productId}/publish`、`.../unpublish`、`.../discontinue`
- `PUT /api/v1/products/{productId}/attributes`（整组替换）
- `POST /api/v1/products/{productId}/skus`、`PUT /api/v1/skus/{skuId}`

状态流转：DRAFT→PUBLISHED→UNPUBLISHED（可再 PUBLISHED）；任意非停售状态→DISCONTINUED（终态、幂等）。非法流转 409。

## 授权与隔离

- 身份只来自认证 Actor；公开接口只按 URL 店铺编码定位 ACTIVE 商户；请求体不允许携带 merchantId/productId/skuId 覆盖归属（DTO 拒绝未知字段）。
- 所有 Mapper SQL 带 `merchant_id` 范围；跨商户、不存在统一 404，不泄露存在性。
- Controller 不访问 Mapper；写操作在 `ProductCommandService`，读操作在 `ProductQueryService`。

## Agent 接入点

- `ProductQueryService.findVisibleProduct(merchantId, productId)`、`findVisibleSku(merchantId, skuId)`、`listVisibleProducts(merchantId, limit)`：只返回 PUBLISHED 商品与 ACTIVE SKU 及其属性/规格事实；不可见返回 null。后续商品 Tool 必须传入可信商户上下文，不接受模型自报 ID。

## 错误码

400/10001 参数无效；401/10002 未认证；403/10003 越权；404/10004 不存在或跨商户（统一不可见）；409/10009 编码冲突、分类深度超限、状态流转非法；503/10500 存储不可用；500/10500 内部错误。

## 测试

- `ProductRequestsTest`：DTO 校验（无容器）。
- `ProductPersistenceIT`（Testcontainers MySQL，需 Docker）：四表租户隔离、可见性过滤、编码唯一、状态流转与停售级联、`@Sql` fixture 的角色可见性/隔离/Agent 事实。
- 本地无 Docker 时按仓库惯例 `mvn -f api/pom.xml test` 只跑单元测试；`verify` 需启动 Docker Desktop。
