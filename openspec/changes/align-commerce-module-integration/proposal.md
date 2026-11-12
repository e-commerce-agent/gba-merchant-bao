# Proposal

## Why

商品、库存、订单和物流已经分别实现，但当前 dev 演示数据的 SKU/商品标识没有对齐，库存也未关联商品目录；部分服务边界仍直接依赖 Mapper 或可能在授权前读取物流。V5 会话表已经在本地 dev 库执行，继续按当前 V6 物流、V7 订单、V8 库存、V9 商品顺序启动无法保证从商品基础数据到订单和物流的完整依赖关系。

库存的 `inventory`、`inventory_transaction`、`inventory_idempotent` 仍使用自增主键，与认证、商品、会话等新业务模块的应用分配 Snowflake 约定存在差异。用户决定在综合验收和本地 V5 升级前完成统一，避免后续开发继续依赖数据库回填主键。部署目标仍为 Nginx 分发到多个同一单体应用的副本，共享 MySQL 和中间件；统一雪花是工程约定，并非 Nginx 负载均衡的必要条件。

## What Changes

- 统一商品/SKU、库存、订单项和物流所使用的稳定演示 ID、订单号及商户归属；为库存补齐依赖真实 SKU 的 dev 数据，并保留跨租户隔离样例。
- 补齐模块间 Java 服务调用：库存按可信商户校验 SKU 存在及归属，商品可售状态与库存状态按模块职责聚合；物流在读取任何运单前先经订单服务验证 Actor 对订单的访问权。
- 对齐认证归档的公开店铺定位约定：商品公开列表、商品详情和 SKU 详情改为 `/api/v1/stores/{storeSlug}/...`，移除原 `/public` 前缀并精确登记匿名 GET 白名单；storeSlug 初期复用商家编码，不新增数据库字段，不改变员工登录或 SOAP 的 merchantCode。同步当前契约、文档及调用地址，不保留旧商品路由别名。
- 在尚未执行的 V6–V9 结构迁移中增加同租户复合外键：库存到 SKU、订单项到所属商品/SKU、物流到订单；业务规则仍由领域 Service 校验。调整迁移与 repeatable dev seed 顺序，使 V5 后先建商品，再建库存、订单、物流；V1–V5 保持不变，并同步更新维护工具、测试和文档。
- 将新 V7 中三张库存表的主键改为应用分配 BIGINT，运行时复用现有单例 `common.id.IdGenerator`，移除 AUTO_INCREMENT 和 generated-key 回填；同步首次库存创建、流水、幂等记录及 seed/fixture 的显式 ID。保持 merchantId/SKU 关联、库存数量/version、事务重试和幂等成功重放规则，补充多实例不同 workerId 的验证和部署说明。
- 保留订单和物流的只读业务范围、订单项成交快照及其现有接口语义；商品公开查询按上述新路径提供，继续遵守原可见性与租户隔离规则；不引入消费者下单、支付、取消、自动库存预占/扣减/释放或 Agent 写操作。
- 明确本次只需兼容当前本地 dev 数据库（已执行到 V5）；不对旧 V6–V9 已执行的其他数据库提供原位重排兼容。

## Capabilities

### New Capabilities
- `commerce-module-integration`: 定义商品、SKU、库存、订单、物流之间的稳定关联、租户校验、模块服务调用、库存雪花主键及 dev 演示数据完整性。

### Modified Capabilities
无。既有电商只读范围、迁移不可变性和环境数据分层规则继续适用；本 change 仅新增跨模块集成契约。

## Impact

- 迁移与种子：`api/src/main/resources/db/migration/`、`db/devdata/`、Flyway locations 和 dev seed 注册/升级代码。
- 服务实现：`product`、`inventory`、`order`、`shipment` 的 Service、授权边界及内部调用契约。
- 库存主键：库存服务、Mapper/XML、PO 传参、R050 种子、所有相关 fixture/测试 SQL；复用现有 IdGenerator，不新增独立生成器、worker 分配服务或新中间件。未来 RAG、Function Calling、文档任务与 ChatMemory 各用自己的领域标识，不依赖库存内部主键。
- HTTP 契约：商品公开 Controller、认证匿名 GET 匹配规则、导出的 OpenAPI 及后端调用示例；前端地址仅只读核对并记录队友接入事项，不修改前端代码，不迁移 Cookie 或引入店铺子域名。
- 测试：迁移顺序和升级验证、跨模块 Service/HTTP 集成、fixture 与 dev seed 稳定 ID 对齐、库存显式主键持久化及多 worker 生成验证。
- 文档：模块数据设计、迁移编号、dev seed 顺序、本地启动/验证及每个存活应用实例独立配置 `MERCHANT_ID_WORKER_ID` 的说明。本次不实施真实 Nginx 集群部署或后续 Agent 能力。
