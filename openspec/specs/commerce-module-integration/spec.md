# commerce-module-integration Specification

## Purpose

定义商品、SKU、库存、订单与物流模块之间可验证的业务关联、租户边界和服务调用契约，使各模块在当前只读订单范围内使用同一批商品与演示数据，并能从本地 V5 数据库顺序启动。

## Requirements

### Requirement: Inventory records shall reference an existing SKU in the same merchant

库存记录及库存变更 MUST 关联真实商品目录中的 SKU，并校验 SKU 与库存使用相同的可信商户范围；未知 SKU、跨商户 SKU 不得通过库存写入建立关联。有效 SKU 可在商品尚未发布时备货；停售 SKU 不得新增可售供应或新增预占，但允许对已有库存执行负向调整及对已有预占执行释放、扣减。

#### Scenario: Staff initializes stock for an owned active SKU
- **WHEN** 商户员工为本商户存在且有效的 SKU 初始化或调整库存
- **THEN** 库存服务接受该操作并按现有幂等、乐观锁和流水规则记录库存

#### Scenario: Stock write references a foreign or missing SKU
- **WHEN** 库存操作使用不存在的 SKU，或 SKU 属于另一个商户
- **THEN** 服务拒绝操作且不创建或变更库存与库存流水

#### Scenario: Stock write references a discontinued SKU
- **WHEN** 员工尝试为已停售 SKU 新建或增加可售库存
- **THEN** 服务拒绝新增可售供应，且不暴露其他商户 SKU 是否存在

#### Scenario: Staff stocks an active SKU before publishing the product
- **WHEN** 本商户商品尚未发布，但其 SKU 有效且员工发起合法正向调整
- **THEN** 服务允许备货，且不因此使商品变为公开可见

#### Scenario: First positive adjustment initializes stock
- **WHEN** 本商户有效 SKU 尚无库存行，员工发起合法正向调整
- **THEN** 服务在库存事务内从零初始化并原子写入数量和流水，并发首次调整及重复幂等键不产生重复库存或重复流水

#### Scenario: Staff clears stock after a SKU was discontinued
- **WHEN** 本商户 SKU 已停售，员工对已有库存负向调整或释放、扣减已有预占
- **THEN** 服务按现有数量、幂等和权限规则允许收尾操作，同时继续拒绝新增预占

### Requirement: Inventory primary keys shall use the shared application Snowflake generator

`inventory`、`inventory_transaction`、`inventory_idempotent` 的 id MUST 使用应用分配的 BIGINT 主键，MUST NOT 使用 AUTO_INCREMENT、数据库默认生成或 generated-key 回填。运行时新记录 MUST 在 INSERT 前由服务端通过现有单例 `common.id.IdGenerator` 分配 ID，并显式写入；不得为库存另建生成算法，也不得从客户端接受内部主键。

#### Scenario: A first positive adjustment creates stock and its audit records
- **WHEN** 合法员工首次正向调整本商户有效 SKU 的库存
- **THEN** 新库存、变动流水及新幂等记录均保存服务端显式分配的 ID；库存与流水仍原子提交，三表结构均不含 auto_increment

### Requirement: Inventory ID changes shall preserve transaction and idempotency behavior

主键统一 MUST 保持可信 merchantId、同商户 SKU 关联、商户/SKU 唯一键、数量/version、事务重试、流水原子性及既有幂等键范围和成功重放行为。失败或重复键竞争允许消耗未落库的 ID，不要求连续；ID MUST NOT 被当作资源权限、业务幂等键或严格提交顺序。

#### Scenario: Concurrent creation and successful idempotent replay use explicit IDs
- **WHEN** 并发首次调整同一商户 SKU，或重放已成功的幂等请求
- **THEN** 商户/SKU 唯一键和现有重试机制保证只有一条库存、每次成功操作一条流水，成功重放不新增流水或幂等记录；未落库 ID 可被丢弃

#### Scenario: The shared generator fails during an inventory write
- **WHEN** ID 生成因时钟回退、序列耗尽或其他错误而失败
- **THEN** 请求按既有错误契约失败，不回退到自增或另一生成器，不留下部分库存/流水提交，也不将本次失败标记为幂等 SUCCESS

### Requirement: Inventory seeds and fixtures shall provide stable explicit IDs

dev seed 与 test fixture MUST 使用各自独立登记的固定显式 ID，作为可复现数据的例外；受控重跑 MUST 只补缺失并保留人工值，固定 ID/自然键归属冲突 MUST 明确失败。不得为了统一生成策略重编号既有真实记录。

#### Scenario: Inventory seeds and fixtures run without database-generated IDs
- **WHEN** 在隔离数据库加载修订后的 R050 或库存相关 fixture、测试 SQL 并受控重复执行适用种子
- **THEN** 所有库存三表 INSERT 显式提供 ID，固定标识与商户/SKU 归属一致，重复种子不覆盖数量/version，冲突明确失败，测试不依赖自增起点或 ID 连续性

### Requirement: Application replicas shall follow the existing Snowflake worker contract

多个同一单体应用副本经 Nginx 共享 MySQL 和中间件时，各存活实例 MUST 配置不同的 `MERCHANT_ID_WORKER_ID`，包括滚动发布中并存的新旧实例；每个实例内部 MUST 复用现有单例 IdGenerator，遵守既有 epoch、worker 范围及重启/时钟约束。不得将默认 worker 0 复制到所有实例，也不得将雪花主键替代租户过滤、Actor 授权或业务幂等。

本 requirement 只规定和验证主键配置及生成行为，不要求本 change 部署真实 Nginx 集群、引入自动 worker 租约或实现分库合并。后续 RAG、文档异步任务及会话使用各自领域标识，Function Calling 经可信身份调用现有 Service，不将库存内部主键作为集成前提。

#### Scenario: Distinct workers generate IDs concurrently in the same millisecond
- **WHEN** 受控测试以不同 workerId 的生成器模拟两个同时存活的应用实例，在同一毫秒并发生成 ID
- **THEN** 生成的 ID 不重复，库存写入沿用同一生成器接口与显式主键契约，不依赖数据库回填

### Requirement: Public catalog routes shall use the shared store locator convention

商品公开查询 MUST 以 `/api/v1/stores/{storeSlug}` 为基路径，提供 GET `/products`、`/products/{productId}`、`/skus/{skuId}`，替代旧 `/public` 商品路径且不保留别名。storeSlug MUST 按认证既有编码规则定位 ACTIVE 商户，初期复用 `merchant_code`，不新增 slug 字段；员工登录、SOAP 的 merchantCode 及内部 merchantId 语义保持不变。

系统 MUST 仅放行上述三个商品 GET 路径，不得放行整个 `/stores/**`。公开读取 MUST 仅按 URL 店铺定位，忽略 token、不推进会话；既有认证入口与员工权限继续有效。当前 OpenAPI、文档、后端测试和调用示例 MUST 与新路径一致；前端调用仅只读核对，差异 MUST 记录为队友接入事项，不修改前端代码。

#### Scenario: Anonymous visitor reads the catalog through the shared store path
- **WHEN** 未登录访客使用上述任一新路径请求 ACTIVE 店铺的公开商品或 SKU
- **THEN** 服务按 storeSlug 解析商户并执行现有可见性、租户隔离和库存布尔组合规则；未知或停用店铺、不可见或跨店资源统一返回 404

#### Scenario: A public request carries an invalid or another store's token
- **WHEN** 访客查询 B 店公开商品，同时携带无效 token 或 A 店 token
- **THEN** 服务仍按 URL 的 B 店公开范围读取，不因 token 改用 A 店或拒绝匿名读取，不建立认证身份或推进会话

#### Scenario: Store paths outside the public catalog remain protected
- **WHEN** 匿名主体请求未声明为公开入口的店铺私有资源，或尝试商品维护写操作
- **THEN** 安全规则不因 `/stores/` 前缀而放行，继续执行现有认证与角色校验；原消费者短信和登录入口仍可按原规则访问

#### Scenario: The exported contract replaces the old public catalog routes
- **WHEN** 导出并检查本次商品 API 契约及对应调用地址
- **THEN** 三个公开读取入口使用 `/api/v1/stores/{storeSlug}/...`，原带 `/public` 的商品路由不再注册或声明，员工登录和 SOAP 的 merchantCode 契约及数据库 `merchant_code` 不变

### Requirement: Product availability shall combine product visibility with stock availability

商品面向消费者或匿名查询的可售状态 MUST 同时满足商品已发布、SKU 有效且本商户 SKU 存在可售库存；公开响应只给有货/无货状态，不暴露精确数量、预占量或库存流水。

#### Scenario: Published SKU has available stock
- **WHEN** 访客查询已发布商品中本商户有效 SKU，且该 SKU 存在可售库存
- **THEN** 商品/SKU 查询返回有货状态

#### Scenario: SKU stock is missing or exhausted
- **WHEN** 访客查询已发布 SKU，但该租户不存在库存记录或可售数量为零
- **THEN** 查询正常返回无货状态，不合成库存数量

#### Scenario: Product or SKU is not publicly visible
- **WHEN** 访客请求未发布商品、停售商品或停售 SKU 的库存可售状态
- **THEN** 响应遵守商品现有公开可见性规则，不泄露不可见商品或其他商户的数据

#### Scenario: Stock changes after the product response was cached
- **WHEN** 商品公开响应已经缓存，随后库存成功变更并再次查询该 SKU
- **THEN** 有货/无货反映已提交的最新可售库存，不沿用商品缓存中的旧库存状态

### Requirement: Order items shall trace to the merchant's catalog while preserving trade snapshots

订单项的 `product_id` 与 `sku_id` MUST 对应同一商户目录中的真实商品和 SKU；订单读取 MUST 继续使用持久化的名称、规格、成交价格和数量快照，不得用当前商品资料覆盖历史成交含义。

#### Scenario: Seeded order item references its catalog SKU
- **WHEN** dev 加载或检查订单项演示数据
- **THEN** 每个订单项均能在相同 merchant_id 下解析到对应商品及 SKU，且 SKU 属于该商品

#### Scenario: Catalog changes after an order was placed
- **WHEN** 当前商品名称、SKU 规格、价格或销售状态发生变化后查询历史订单
- **THEN** 订单详情仍返回原订单项保存的成交快照，商品与 SKU ID 仅供追溯

#### Scenario: An order item references a foreign or missing catalog row
- **WHEN** 一条订单项写入时商品或 SKU 不存在，SKU 不属于该商品，或商户归属不一致
- **THEN** 持久化边界拒绝该关联，且不能破坏既有订单项快照

### Requirement: Shipment lookup shall authorize the order before reading shipment details

按订单查询物流 MUST 先使用可信 Actor 通过订单域服务确认消费者本人或员工本商户的订单访问权，再按该订单及同一商户读取物流；请求 ID、订单号、运单号和对话记忆中的值仅能作为定位信息。

#### Scenario: Authorized principal reads an order shipment
- **WHEN** 消费者查询本人订单，或员工查询本商户订单的物流
- **THEN** 订单域授权成功后物流服务返回该订单的物流状态和轨迹

#### Scenario: A shipment exists for an order the actor cannot access
- **WHEN** 当前主体请求他人、跨租户或不存在订单的物流，即使该订单存在运单记录
- **THEN** 系统在读取或返回运单信息前统一返回订单查询约定的 404，不泄露物流是否存在

#### Scenario: Authorized order has no shipment
- **WHEN** 订单域确认主体有权读取该订单，但尚无物流记录
- **THEN** 物流服务返回既定的 NOT_SHIPPED 空轨迹响应

#### Scenario: Service client queries an order through SOAP
- **WHEN** 持有效服务间凭证的 SOAP 客户端使用 merchantCode 与 orderNo 查询物流
- **THEN** 系统先通过订单域确认该商户的真实订单再读取物流；真实订单无运单返回 NOT_SHIPPED，未知订单返回 Fault，消费者的 Bearer 凭据不能替代服务间凭证

### Requirement: Persisted commerce relationships shall enforce same-merchant referential integrity

数据库 MUST 拒绝不存在或跨商户的库存到 SKU、订单项到商品/SKU、物流到订单以及轨迹到运单的引用；订单项中的 SKU MUST 属于同一行关联的商品。关联约束不得级联删除历史订单、成交快照或库存流水，且不能替代服务授权与业务状态校验。

#### Scenario: SQL attempts to insert a foreign merchant relation
- **WHEN** 绕过应用直接插入跨商户库存、订单项、运单或轨迹引用
- **THEN** 数据库拒绝写入，即使被引用的全局 ID 存在

#### Scenario: SQL attempts to insert an orphan or mismatched SKU relation
- **WHEN** 记录引用不存在的父记录，或订单项同时指定互不对应的商品和 SKU
- **THEN** 数据库拒绝写入，不留下孤儿记录或错误商品归属

### Requirement: Commerce module migrations and development seeds shall follow dependency order

在当前 V5 数据库基线上，版本化结构迁移 MUST 先创建商品目录，再创建依赖商品 SKU 的库存、订单和物流结构；dev repeatable seeds MUST 在租户与账号后依次加载商品、库存、订单和物流。测试 fixture MUST 使用匹配的商品/SKU/订单标识；test/prod MUST NOT 加载 dev 演示业务数据。

库存雪花主键调整 MUST 在此兼容范围内修改尚未执行的新 V7，保持 V1–V5 与 R010/020/030 不变，不为此新增 V10。若本地目标已应用 V7 或不再符合 V5 基线，MUST 停止该目标的升级并另行评审向前迁移，不得 repair、改 history/checksum 或删除业务卷。

#### Scenario: Fresh dev database starts from the current V5 baseline
- **WHEN** 本地 dev 数据库的 Flyway 历史仅成功到 V5，且应用按约定激活 dev 启动
- **THEN** Flyway 按有序的 V6 商品、V7 库存、V8 订单、V9 物流结构迁移和租户/账号后的相依 dev seed 完成初始化，库存三表无自增且种子有显式 ID，应用可继续启动

#### Scenario: The local target no longer matches the unexecuted inventory migration assumption
- **WHEN** 本地升级前只读核对发现目标已应用 V7 或其他新迁移，不再是本 change 约定的 V5 基线
- **THEN** 停止该目标的升级并另行评审，不执行修订后的 V7、不改写 history/checksum 或真实记录主键

#### Scenario: Seed identities link the commerce demonstration flow
- **WHEN** dev 完成全部业务演示种子
- **THEN** 商品 SKU、库存记录、订单项与物流记录按稳定 ID、merchant_id、order_id/order_no 相互对应，并保留隔离租户负向样例

#### Scenario: Test and production locations are migrated
- **WHEN** test 或 prod 执行数据库初始化
- **THEN** 结构按同一迁移依赖顺序建立；test 只用显式 fixture，prod 只使用允许的迁移/基础种子，不加载 dev 演示商品、库存、订单或物流数据

#### Scenario: Existing local seed data has manual changes
- **WHEN** dev 种子因显式受控补种而执行
- **THEN** 仅补缺失的稳定演示记录并检查身份冲突，不覆盖人工修改，不自动执行 repair、删除数据卷或伪造 Flyway 历史

### Requirement: Cross-module calls shall use domain services and trusted merchant context

跨域业务规则 MUST 通过提供方领域服务调用；调用方 MUST 传递从可信 Actor 或服务端租户解析得到的 merchantId，并由提供方再次检查资源归属与可见状态。Controller 和其他模块 MUST NOT 绕过服务直接访问对方 Mapper。

#### Scenario: Product query aggregates stock availability
- **WHEN** 商品公开查询需要返回 SKU 可售状态
- **THEN** 商品查询调用库存的只读可售能力，并仅组合返回商品允许公开的有货/无货事实

#### Scenario: Inventory validates SKU through the product boundary
- **WHEN** 库存服务创建或变更某商户 SKU 的库存
- **THEN** 它调用商品域的只读 SKU 归属/销售状态能力校验，而不直接读取商品 Mapper

#### Scenario: A cross-module call receives a foreign tenant identifier
- **WHEN** 调用参数中的 merchantId 与资源真实归属不一致
- **THEN** 提供方拒绝操作或按该业务查询约定返回不可见结果，不信任调用方传入的租户值

#### Scenario: Read-only order scope remains intact
- **WHEN** 本 change 的业务模块集成完成
- **THEN** 系统仍不提供下单、支付、取消订单、订单状态写入或 Agent 库存/订单/物流写操作
