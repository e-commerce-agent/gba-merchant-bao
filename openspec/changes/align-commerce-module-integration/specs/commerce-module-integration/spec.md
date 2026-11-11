# Spec Delta

## Purpose

定义商品、SKU、库存、订单与物流模块之间可验证的业务关联、租户边界和服务调用契约，使各模块在当前只读订单范围内使用同一批商品与演示数据，并能从本地 V5 数据库顺序启动。

## ADDED Requirements

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

#### Scenario: Fresh dev database starts from the current V5 baseline
- **WHEN** 本地 dev 数据库的 Flyway 历史仅成功到 V5，且应用按约定激活 dev 启动
- **THEN** Flyway 按有序的 V6 商品、V7 库存、V8 订单、V9 物流结构迁移和租户/账号后的相依 dev seed 完成初始化，应用可继续启动

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
