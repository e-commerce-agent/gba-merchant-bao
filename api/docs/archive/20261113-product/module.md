# 商品模块（product）

## 范围

管理 3C 商品及其可售规格：分类（两级）、商品/SPU、SKU、结构化兼容性属性；提供公开商城查询、登录读取与商户员工维护接口，并向后续 Agent 商品 Tool 输出 `ProductQueryService` 只读事实。库存、订单、物流依赖本模块冻结的 SKU 标识与商户归属。

- change：`openspec/changes/add-product-module/`
- 集成对齐归档：`openspec/changes/archive/2026-11-12-align-commerce-module-integration/` 第 4 节（公开店铺路径、匿名访问边界及库存布尔）
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

公开商城读路径是本模块的高并发落点，缓存只在 product 域内，不读写认证会话；匿名路由白名单按下文单独登记：

- 缓存对象：`ProductCatalog` 中的目录事实（商品、ACTIVE SKU、属性），以及无 cursor 的公开首页（按 store+category+size）；`storeSlug → merchantId` 映射另缓存 60 秒，已缓存店铺停用后最多延迟 60 秒不可见。缓存不保存 `inStock` 或库存数量。
- 键规范：条目 `gba:product:catalog:v2:e:{merchantId}:v{ver}:{kind}:{id}`，版本 `gba:product:catalog:v2:ver:{merchantId}`，映射 `gba:product:store:{storeSlug}`；storeSlug 先按认证规则 trim、转大写并校验格式，再查询或写缓存，不因大小写产生多个映射。首页 id 为 `{categoryId或0}:size:{size}`。v2 命名空间使旧格式条目自然失效，不读取旧条目或执行全库清理。
- 失效：任何商户写操作（分类增改、商品增改、上下架、停售、属性替换、SKU 增改）`INCR` 该商户版本号，旧条目自然失效（TTL 15 分钟兜底），无需遍历删除
- 降级：Redis 不可用/超时/JSON 损坏一律当作未命中回源数据库，缓存永不成为依赖；`merchant.product.cache-enabled=false` 可整体关闭（测试环境已关闭）
- 一致性边界：缓存只覆盖匿名公开读；员工/消费者已登录读、Agent 事实查询始终直读数据库，保证管理端与 Agent 看到最新状态
- 库存实时性：公开请求即使命中目录缓存，仍按店铺解析出的 merchantId 和本次可见 SKU 集合调用 `InventoryAvailabilityQueryService.inStock`，一页只执行一次批量库存查询，再构造新的公开响应。库存写入无需失效商品目录缓存；库存数据库故障返回 503/10500，不伪装为缺货。

## 接口契约

公开（匿名 GET，storeSlug 按认证既有编码规则解析 ACTIVE 商户，格式无效、未知或停用店铺返回 404；映射缓存时效见上文）：

- `GET /api/v1/stores/{storeSlug}/products`：PUBLISHED 分页；`size` 1–100 默认 20；`cursor` 不透明游标
- `GET /api/v1/stores/{storeSlug}/products/{productId}`：详情（含 ACTIVE SKU 与属性）
- `GET /api/v1/stores/{storeSlug}/skus/{skuId}`

storeSlug 是公开店铺定位符，初期复用数据库 `merchant_code`；例如 `/api/v1/stores/M_3C_DEMO/products`。不新增 slug 表字段，员工登录及 SOAP 的 `merchantCode`、内部 `merchantId` 均保持不变。此次替换原带 `/public` 的商品路径，不保留旧路由别名；旧路径匿名访问返回 401，带有效凭证也无法访问旧商品资源。

安全规则仅匿名放行以上三个资源路径的 GET，不放行整个 `/stores/**`；即使携带无效 token 或其他店铺 token，公开请求仍按 URL 店铺查询，不建立身份、不认证或续期会话。现有消费者验证码/登录 POST 白名单保留，其他私有资源与商品维护写操作继续受认证和角色限制。

后端调用示例：`GET /api/v1/stores/M_3C_DEMO/skus/2500000000000301`，无需 Authorization。`inStock` 已包含于公开 SKU 响应，不提供独立匿名 `/availability` 接口。

前端接入事项（仅只读核对，代码由队友处理）：`view/src/api/catalog.ts` 的列表及商品详情已使用店铺路径；`getSku` 仍调用需登录的 `/v1/skus/{skuId}`，公开使用时应改为 `/v1/stores/{storeSlug}/skus/{skuId}`（默认 `/api` 基址）。`getSkuAvailability` 使用的 `/v1/skus/{skuId}/availability` 不是后端公开契约，应从公开 SKU 的 `inStock` 取值。本次未修改前端代码，也未验证页面联调。

以上列表/商品详情内的每个 SKU，以及公开 SKU 详情，均包含必填布尔字段 `inStock`。它表示该店铺该 SKU 的当前可售库存是否大于零；库存缺行或可售量为零时为 `false`，即使仍有预占量也不算有货。它不承诺后续预占必定成功。

先执行现有 ACTIVE 店铺、PUBLISHED 商品和 ACTIVE SKU 可见性规则，再读取库存。未发布/停售商品、停售 SKU、未知或其他商户资源遵循原有过滤/统一 404 语义；列表/详情不包含不可见 SKU。匿名访客和登录消费者均可调用这些公开接口，只获得有货/无货，不返回 `availableQty`、`reservedQty`、总量、version 或流水。员工精确库存仍需通过 `/api/v1/inventory` 的角色校验；本次未扩展登录管理响应和 Agent 事实字段。

公开 SKU 响应示例（字段节选）：

```json
{"skuId":"2600000000000301","skuCode":"FIX-SKU-1","title":"Fixture SKU 一","price":"99.00","attributes":{"兼容机型":"iPhone 15;小米 14"},"inStock":true}
```

机读契约见 [product-v1.openapi.yaml](../../../docs/api/product-v1.openapi.yaml)，由隔离后端实际 Springdoc `product-v1` 分组导出；`ProductAvailabilityIT` 核对 `PublicSkuView.inStock` 的 boolean/required 定义、嵌套 SKU 引用及接口实测结果。

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
- `InventoryAvailabilityQueryServiceTest`：批量商户范围、空集合、缺行映射和存储异常传播。
- `ProductCacheStoreTest`：纯目录 JSON、v2 格式隔离、版本失效和 Redis 降级。
- `PublicCatalogSecurityTest`：精确匹配三个匿名 GET、拒绝额外店铺子路径/写方法/旧公开前缀，保留消费者认证入口；公开请求忽略凭证、不建立认证身份或访问会话。
- `ProductAvailabilityIT`（隔离 MySQL/Redis）：匿名/消费者公开布尔、不泄漏精确库存、缺行/零可售/预占、两租户及停售可见性、批量分页、缓存预热后的预占/释放实时性、Redis 故障回源和 MySQL 故障 503；覆盖新路径、slug 规范化/规范缓存键、无效及停用店铺、无效/异店 token、旧路由移除和私有/员工访问权限，并导出实际 OpenAPI 到 `api/target/product-v1.openapi.yaml`。
- 本地无 Docker 时按仓库惯例 `mvn -f api/pom.xml test` 只跑单元测试；`verify` 需启动 Docker Desktop。

定向回归结果见 [本目录 README](README.md#catalog-verification)。
