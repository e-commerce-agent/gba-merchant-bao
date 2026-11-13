# 库存模块（inventory）

库存模块（队友交付）按主仓规范合入：纯 MyBatis + XML（无 MyBatis-Plus），身份只来自
`@AuthenticationPrincipal Actor`（`auth.security.Actor`，record），统一主仓 `ApiResponse`
envelope，业务错误码走 13xxx 段。

## 资源与语义

- `POST /api/v1/inventory/reserve`：预占（可售 -qty、预占 +qty）
- `POST /api/v1/inventory/release`：释放预占（可售 +qty、预占 -qty）
- `POST /api/v1/inventory/deduct`：扣减预占（支付成功/发货出库）
- `POST /api/v1/inventory/adjust`：人工调整（正数入库、负数盘亏）
- `GET  /api/v1/inventory/{skuId}`：单个 SKU 库存（本商户）
- `GET  /api/v1/inventory/page?pageNo&pageSize`：分页列表（pageSize 上限 100）

全部库存 HTTP 路由仅供本商户员工使用，包括单 SKU 精确库存和分页查询；未认证返回 401，消费者访问沿用库存角色拒绝约定 400/13006，且不返回库存数据。请求格式、状态码和调用示例见 [库存接口说明（本目录入口）](README.md#api-design)。公开商品通过只依赖库存 Mapper 的 `InventoryAvailabilityQueryService.inStock(merchantId, skuIds)` 批量组合当前有货/无货，缺行或零可售为 false，不暴露精确库存；目录缓存命中后仍读取当前库存。详见 [商品模块（本目录入口）](README.md#product-module)。

统一基路径为 `/api/v1/inventory`，旧商户前缀路径已停用。路径调整不改变多商户模型：商户仍来自可信 Actor，所有 `merchant_id` 字段及同商户关联约束保留，无需修改库存、商品、订单或物流表结构。

## 关键设计

- **幂等**：`inventory_idempotent` 唯一键 + INSERT 抢占（不先查后写）；SUCCESS 重复请求按成功返回，
  FAILED 可 CAS 抢回重试，PROCESSING 返回 13004。
- **不超卖**：乐观锁 `version` 条件更新；每轮重试 `REQUIRES_NEW` 新事务重读最新数据、重新校验
  （RR 快照下同一事务重读无效），重试 3 次仍冲突返回 13003。
- **租户隔离**：所有 SQL 带 `merchant_id` 谓词；跨租户与不存在统一 404/13001。
- **流水留痕**：`inventory_transaction` 记录变更前后值、幂等键、操作人（accountId）。
- **主键**：`inventory`、`inventory_transaction`、`inventory_idempotent` 运行时共用 Spring 单例 `common.id.IdGenerator`；创建前分配雪花 ID，Mapper 显式 INSERT id，不使用数据库自增或 generated-key 回填。HTTP 不接收这些内部主键。
- **目录引用**：写操作经商品域 `ProductReferenceService.findSku` 查询本商户 SKU 的商品归属及状态，不跨域访问商品 Mapper，也不依赖商品公开聚合、Redis 或库存查询。未知与跨商户 SKU 统一 404/13001。
- **发布前备货**：DRAFT/UNPUBLISHED 商品的 ACTIVE SKU 可备货；不改变商品发布状态。停售 SKU 的正向 adjust 与 reserve 返回 409/13008；已有库存的负向 adjust，以及已有预占的 release/deduct 仍按原数量规则处理。
- **首次调整**：只有正向 adjust 可在缺行时从 0 初始化；初始化、带 version 的数量更新和流水在同一个事务提交。首次成功后 version=1、预占=0、流水 before=0；后续每次成功 version 加 1。负向 adjust 或 reserve/release/deduct 缺行返回 404/13001。
- **并发初始化**：`uk_merchant_sku(merchant_id,sku_id)` 裁定首次创建，唯一键竞争或创建锁冲突回滚后沿现有最多 3 轮新事务重试；不会覆盖另一请求已提交的数量/version。
- **重放边界**：每次先检查员工身份与当前目录归属/状态，再处理幂等成功重放。成功重放仍不重复写库存或流水，但 SKU 已停售时重复 reserve/正向 adjust 也会返回 13008。幂等键沿用现有全局唯一约定，调用方须为独立业务操作生成独立键。

这些操作仍是员工维护入口，不提供消费者下单、支付、取消接口，也不由订单查询自动调用库存写操作。

## 验证

`ProductReferenceServiceTest` 覆盖目录状态和归属边界；`InventoryFlowIT` 在隔离 Testcontainers MySQL/Redis 及真实 Spring 上下文中验证未发布商品备货、停售收尾、首次/并发调整、幂等重放、乐观锁重试、流水失败原子回滚及 HTTP 权限，并检查三类记录确实保存注入生成器分配的 ID、FAILED 重试复用原幂等记录，以及分配失败时的事务回滚。`InventorySchemaIT` 检查三表主键元数据与省略 id 的拒绝行为。

```powershell
mvn -f api/pom.xml '-Dtest=MigrationScriptContractTest,SnowflakeIdGeneratorTest,ProductReferenceServiceTest' '-Dit.test=InventorySchemaIT,InventoryFlowIT' test-compile surefire:test failsafe:integration-test failsafe:verify
```

需要 Docker Linux 容器引擎；不会连接本地 dev 数据库。
第 6 节库存雪花主键的实际命令、结果和范围见 [定向验收记录（本目录入口）](README.md#snowflake-verification)。

## Flyway 编号

`V7__inventory_tables.sql` 在商品目录 `V6__product_module.sql` 后创建库存结构，并用同商户复合外键引用 `product_sku`。dev 种子为 `R__050_inventory_demo.sql`，依赖 `R__040_product_demo.sql`；只初始化缺失库存，重跑不覆盖人工数量/version，也不创建库存流水。V1–V5 与 R010/R020/R030 保持冻结。

库存及流水都用 `(merchant_id, sku_id)` 引用同商户 SKU，限制删除父 SKU，不级联删除流水。当前顺序只兼容新库及本地尚在 V5 的 dev 基线，不兼容已执行旧 V6–V9 布局的其他库。演示库存覆盖 SKU `2500000000000301–307/309`，含有货、缺货、预占和停售保留量；完整 ID 对照及补种步骤见 [dev 数据登记（本目录入口）](README.md#devdata)。

## 主键与多副本运行

运行时的雪花 ID 与 seed/fixture 的固定 ID 用途不同。R050 使用 `2500000000000501–507/509`；测试 fixture 使用独立的 `260...` 空间，具体登记见 [dev 数据说明（本目录入口）](README.md#devdata)。固定 ID 和 `(merchant_id,sku_id)` 归属冲突须在种子写入前报错。重跑只补缺失库存，保留数量/version；不重编号已有真实记录，不伪造流水。

首次库存创建仍由商户/SKU 唯一键裁定，成功幂等重放不新增业务记录，FAILED 重试沿用原幂等行。重复 INSERT、并发竞争、事务回滚可能消耗未落库的 ID；允许跳号，不回收，不用 ID 连续性或大小推断业务成功、提交顺序。时钟回退或序列耗尽等生成器故障沿既有错误契约失败（HTTP 500/13999），不回退到自增、时间戳或局部计数；事务内库存/流水一起回滚，已取得执行权的幂等行标记 FAILED。

前后端分离的单体后端可以启动多个副本，由 Nginx 反向代理、负载均衡，共用 MySQL/Redis。共享 MySQL 写入时，自增本身也能支持多个应用副本；本次雪花统一是工程约定，不代表完成集群验收。每个同时存活的副本必须独立配置 `MERCHANT_ID_WORKER_ID`（0–1023），包含滚动发布期间的新旧进程；实例内各模块复用同一个生成器。默认 0 只供单实例 dev，不能照搬到所有副本。保持固定 epoch，重启/复用 worker 前确认时钟已超过该 worker 最后使用时间；具体算法与故障约定见 [认证主键契约（本目录入口）](README.md#auth-data-design-ids)。当前没有自动 worker 租约服务。

全局 ID 不替代商户过滤、Actor 授权、业务幂等或 MQ 去重。后续 RAG 的文档/切片、Function Calling 的领域参数、RabbitMQ 的异步任务、Redis ChatMemory 的会话/消息均使用各自领域标识，经既有 Service 访问；本次不新增这些模块或接口。库存业务定位仍使用可信 merchantId + skuId。
