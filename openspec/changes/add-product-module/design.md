# Design

## 1. 数据模型（V6）

四张表全部遵循既有迁移风格：雪花 BIGINT 主键、`merchant_id` 复合外键指向 `merchant(id)`、状态列 `ascii_bin` + CHECK、`DATETIME(3)` UTC 时间由应用显式提供、`utf8mb4_0900_ai_ci`。

| 表 | 关键字段 | 关键约束/索引 |
| --- | --- | --- |
| product_category | id、merchant_id、parent_id、name、sort_no、status | `(merchant_id,parent_id,name)` 唯一；`(merchant_id,id)` 唯一（自引用外键用）；二级深度由 Service 保证（父分类必须是根） |
| product | id、merchant_id、category_id、product_code、name、brand、description、status | `(merchant_id,product_code)` 唯一；`(merchant_id,category_id)` 外键；`(merchant_id,status,updated_at,id)` 分页索引 |
| product_sku | id、merchant_id、product_id、sku_code、title、price、attributes_json、status | `(merchant_id,sku_code)` 唯一；`(merchant_id,product_id)` 外键；`(merchant_id,product_id,status,id)` 列表索引；`price DECIMAL(12,2) >= 0` |
| product_attribute | id、merchant_id、product_id、attr_name、attr_value、searchable | `(merchant_id,product_id,attr_name)` 唯一；`(merchant_id,attr_name,attr_value)` 检索索引 |

状态枚举：商品 `DRAFT/PUBLISHED/UNPUBLISHED/DISCONTINUED`，SKU `ACTIVE/DISCONTINUED`，分类 `ACTIVE/INACTIVE`。属性规则与设计手稿一致：结构化筛选属性存 `product_attribute`（一个属性名一行，多值用分隔符拼接），SKU 展示组合存 `attributes_json`；不建专用列，不把所有属性塞 JSON。

## 2. 路由契约

公开（匿名 GET，店铺编码解析 `ACTIVE` 商户，失败 404）：

- `GET /api/v1/public/stores/{merchantCode}/products`：分页列出 PUBLISHED 商品；`size` 1–100 默认 20，`cursor` 不透明游标。
- `GET /api/v1/public/stores/{merchantCode}/products/{productId}`：PUBLISHED 商品详情，含 ACTIVE SKU 与属性。
- `GET /api/v1/public/stores/{merchantCode}/skus/{skuId}`：PUBLISHED 商品的 ACTIVE SKU 详情。

登录读取（Bearer，任意角色）：`GET /api/v1/products`、`/api/v1/products/{productId}`、`/api/v1/skus/{skuId}`、`GET /api/v1/product-categories`（员工管理视图，含 INACTIVE）。消费者读取时仅返回 PUBLISHED/ACTIVE 内容；员工返回本商户全部状态。

员工维护（仅 MERCHANT_STAFF，非 GET 由安全过滤器角色门禁）：

- `POST /api/v1/product-categories`、`PUT /api/v1/product-categories/{categoryId}`
- `POST /api/v1/products`、`PUT /api/v1/products/{productId}`、`POST /api/v1/products/{productId}/publish`、`POST /api/v1/products/{productId}/unpublish`
- `PUT /api/v1/products/{productId}/attributes`（整组替换）
- `POST /api/v1/products/{productId}/skus`、`PUT /api/v1/skus/{skuId}`

写路由不用 PRD 的 `:publish` 冒号风格，统一用资源子路径（见 spec delta）；PRD 6.3 在需求文档定稿时同步措辞。所有写操作 Controller 不访问 Mapper，Service 从可信 Actor 取 merchantId，请求体不能携带 merchantId/productId 覆盖归属。

## 3. 授权与隔离

- 公开：只按 URL 店铺编码定位 ACTIVE 商户；越界与不存在同为 404，不泄露非公开商品存在性。
- 登录读取与维护：`Actor.principal().merchantId()` 注入每条 SQL；跨商户、不存在统一 404。
- 角色门禁在 `AuthSecurityFilter` 于反序列化前执行（复用 staff-accounts 的模式）：商品域非 GET 必须 MERCHANT_STAFF。
- Agent Tool 接入：`ProductQueryService.findVisibleProduct/findVisibleSku` 接收解析后的商户上下文，不接受模型或请求自报的 merchantId。

## 4. 分页与游标

仿照 chat：游标为 Base64 URL 编码的 `product:{updatedAtMillis}:{id}`（员工视图）或 `public:{updatedAtMillis}:{id}`，解码失败即 400；SQL 按 `(updated_at,id)` 倒序 LIMIT，不使用整表 count。

## 5. 错误与响应

统一 `ApiResponse` envelope + `X-Request-Id`；`ProductError` 映射：400 INVALID_ARGUMENT、401 UNAUTHORIZED、403 FORBIDDEN、404 RESOURCE_NOT_FOUND（含跨商户）、409 CONFLICT（编码重复、父分类非法、停售/已发布状态流转非法）、503 CHAT 风格存储不可用复用 `INTERNAL_ERROR` 上抛。ID 出参一律 `ApiId` 字符串，时间为 UTC ISO-8601。

## 6. 测试策略

- 单元：`ProductRequestsTest` 校验 DTO（未知字段拒绝、长度、价格、状态、编码格式）。
- 集成（Testcontainers MySQL，Docker 缺失时按仓库惯例失败快显）：`ProductPersistenceIT` 覆盖四表租户隔离、发布可见性过滤、编码唯一冲突、属性整组替换、游标分页与 `@Sql` fixture 双租户数据。
- dev 演示数据与 fixture 分离；种子幂等、固定业务编码、缺依赖时明确失败。

## Redis 商品缓存（高并发落点，2026-11-10 追加）

- 只覆盖匿名公开读（公开商品/SKU 详情、无 cursor 首页），不改动 auth 域任何代码与数据。
- 版本化失效：条目键含 `gba:product:ver:{merchantId}` 的当前版本；任何商品域写操作后 `INCR` 版本，O(1) 使旧条目不可达，TTL 15 分钟兜底。
- 可靠性：所有 Redis 操作 try/catch 降级为回源数据库；`merchant.product.cache-enabled` 可关闭（test profile 关闭）。
- 一致性：员工/消费者登录读与 Agent `ProductQueryService` 事实查询不读缓存，避免管理端看到过期状态。
