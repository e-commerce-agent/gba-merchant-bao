# Spec Delta

## MODIFIED Requirements

### Requirement: The application shall be organized by business domain

后端 MUST 以认证、商户、商品、库存、订单、物流、会话、知识库、文档任务、Agent 和评测等业务域组织模块；协议转换、业务规则和持久化访问必须有清晰职责边界，Agent 编排不能复制交易域规则。迁移期间旧餐饮代码 MUST 通过明确的兼容边界接入，不能继续扩大跨域直接依赖。

#### Scenario: Domain service owns business rules
- **WHEN** Agent Tool 或 HTTP Controller 需要查询订单、库存或物流
- **THEN** 调用对应业务域服务并复用其租户、用户归属和隐私规则，不能绕过服务直接访问 Mapper 或数据库

#### Scenario: A new domain does not require renaming old models
- **WHEN** 实现商品、SKU 或订单模块
- **THEN** 使用独立的通用电商模型，并为旧餐饮模型建立明确的适配、迁移或退役关系

#### Scenario: Package boundaries are checked during staged migration
- **WHEN** 一个技术模块从旧包迁移到新业务域
- **THEN** 编译和测试能够识别该模块对旧包的允许适配依赖与禁止的跨域直接访问，且未迁移模块仍可通过兼容边界运行

### Requirement: Protected APIs shall use one versioned contract and server-side identity

受保护接口 MUST 使用统一的 `/api/v1` 契约、稳定 HTTP 状态和业务码，并从认证上下文解析主体、角色和租户；订单、物流、会话及知识库接口必须声明其资源归属规则。新增或迁移接口 MUST 通过同一份 OpenAPI 3 契约提供可生成的请求、响应、错误和鉴权描述。

#### Scenario: A consumer requests a protected resource
- **WHEN** 消费者调用订单、物流或会话接口
- **THEN** 系统先验证登录会话和资源归属，再返回脱敏后的允许字段；缺少权限时返回明确的 401/403 结果

#### Scenario: API contract is documented
- **WHEN** 新增或变更 `/api/v1` 接口
- **THEN** 请求、响应、校验错误、鉴权方案、异步任务状态和 SSE 事件都能从同一份 OpenAPI 契约导出

#### Scenario: A legacy endpoint is migrated incrementally
- **WHEN** 某个旧 `/admin` 或 `/user` 接口尚未完成调用方迁移
- **THEN** 旧接口继续按兼容清单提供服务，同时新接口在 Springdoc 生成的 `/api/v1` 文档中公开迁移后的契约和弃用信息

### Requirement: Schema evolution shall be versioned and reversible

数据库结构 MUST 通过按序、不可修改的版本化迁移演进；旧库接管、新电商表、数据映射和退役步骤必须可追踪。迁移不得删除或重建未核对的旧表，生产变更前必须具备备份、校验和回退或降级路径。

#### Scenario: A fresh database is initialized
- **WHEN** 在空数据库启动应用
- **THEN** 系统按顺序执行基线和后续迁移，建立当前所需的电商表、索引和约束

#### Scenario: An existing restaurant database is migrated
- **WHEN** 对已有旧餐饮库执行重构迁移
- **THEN** 系统先完成结构核对和备份，再按保留、适配、映射或退役清单处理旧表，不静默删除旧数据或把旧表改名伪装成新领域

#### Scenario: A migration has already been applied
- **WHEN** 数据库记录某个迁移版本已执行
- **THEN** 发布流程不得原地修改该版本，而是追加新版本并提供校验结果、失败处理和可执行的降级说明

### Requirement: Infrastructure responsibilities shall be explicit

Redis、RabbitMQ、对象存储、向量数据库和关系数据库 MUST 按职责分工；缓存、异步任务、文档向量化、会话记忆和完整历史不得互相替代，失败状态必须可观测并可恢复。中间件依赖 MUST 由实际使用它的技术模块按需引入，不得在尚未实现的模块完成前作为全局前置能力。

#### Scenario: A dependency is unavailable
- **WHEN** Redis、RabbitMQ、Milvus 或对象存储暂时不可用
- **THEN** 系统按所属模块的降级或重试约定返回可识别错误，并保留可查询的业务状态，不把中间件可用性当作唯一事实来源

#### Scenario: Agent dependencies integrate with the baseline
- **WHEN** 接入既有 Agent 四个亮点
- **THEN** RAG、Function Calling、RabbitMQ 文档任务和 Redis ChatMemory 使用各自的领域接口与数据隔离规则，不改变其已确定的业务场景

#### Scenario: A module introduces a new middleware
- **WHEN** 后续登录模块开始实现并需要 Redis 会话
- **THEN** 该模块的 change 同时声明 Redis Maven 依赖、配置、数据键约定、测试和运维检查；本基础 change 不提前创建登录会话或认证表

### Requirement: The refactor shall preserve compatibility evidence

重构 MUST 为旧接口和旧前端调用建立保留、适配、弃用或退役清单，并记录每个迁移阶段的回归证据；本 change 不得直接删除尚未确认调用方的旧能力。

#### Scenario: A legacy endpoint is still in use
- **WHEN** 旧 `/admin` 或 `/user` 接口仍有调用方
- **THEN** 系统在兼容期保留或提供适配，并明确新旧语义、认证方式和退役条件

#### Scenario: A legacy endpoint is retired
- **WHEN** 旧接口完成调用方确认、数据对账和回归验证
- **THEN** 系统按记录的退役步骤下线，并保留迁移说明和回退策略

## ADDED Requirements

### Requirement: Each technical module shall provide observable test evidence

每个完成迁移的技术模块 MUST 同时提供与其边界匹配的单元、集成或契约测试，并输出带请求关联标识的结构化日志；测试失败、迁移失败和外部依赖不可用时必须能定位到模块和请求。

#### Scenario: A module is marked complete
- **WHEN** 一个技术模块提交为可联调状态
- **THEN** 构建产物包含该模块的测试结果、接口契约校验结果和日志字段检查结果，且结果可关联到对应提交或变更

#### Scenario: A request fails across a module boundary
- **WHEN** HTTP 请求在 Controller、业务服务或外部依赖处失败
- **THEN** 日志包含 requestId、模块标识、稳定错误码和必要的耗时信息，响应不泄露堆栈或敏感凭证

### Requirement: The foundation shall provide deterministic tenant fixtures

基础迁移 MUST 建立 `merchant` 租户根和两个可重复引用的 `local/test` 租户：`M_3C_DEMO` 与 `M_ISOLATION_TEST`。业务模块只能按已冻结的业务编码追加自己的测试数据，生产迁移不得加载演示种子。

#### Scenario: A fresh test database is initialized
- **WHEN** 测试环境从空库执行基础迁移和本地测试种子
- **THEN** 存在且仅存在可识别的样板租户 `M_3C_DEMO` 和对照租户 `M_ISOLATION_TEST`，后续测试通过业务编码解析主键

#### Scenario: A module adds fixture data
- **WHEN** 商品、订单、物流、知识或会话模块新增固定测试数据
- **THEN** 数据使用稳定业务编码和幂等写法，明确 `merchant_id` 归属，不依赖每次变化的自增或 Snowflake 数字 ID

#### Scenario: Production starts with the foundation
- **WHEN** 生产 profile 执行 Flyway
- **THEN** 只执行结构和必要的数据迁移，不加载样板商品、订单、账号、会话或评测数据

### Requirement: Tenant-scoped schema rules shall be enforced at the data boundary

所有商户私有表 MUST 使用非空 `merchant_id`；租户维度唯一约束、高频索引和写入条件 MUST 保持一致，服务端身份上下文 MUST 是租户来源，不能由请求 DTO 或 Agent 参数覆盖。

#### Scenario: A private table is introduced
- **WHEN** 后续模块新增商户私有业务表
- **THEN** 表包含非空 `merchant_id`，唯一约束和主要查询索引以 `merchant_id` 为首列，并按统一主键、审计字段、金额、时间和状态规则设计

#### Scenario: A tenant-scoped row is updated or deleted
- **WHEN** 服务执行更新或删除
- **THEN** SQL 同时使用服务端解析的 `merchant_id` 和资源标识；不能只查询后在 Java 内存中判断租户归属

#### Scenario: A cross-tenant relation is attempted
- **WHEN** 一条关系记录引用其他租户的资源
- **THEN** 数据库约束或服务层写入校验拒绝该关系，不允许仅凭全局资源 ID 建立跨租户引用

### Requirement: New and legacy HTTP response contracts shall remain distinguishable

新 `/api/v1` 普通 JSON 接口 MUST 使用 `code`、`message`、`data`、`requestId` 统一响应，并以 HTTP 状态表达协议结果；旧 `/admin`、`/user` 接口在迁移完成前 MUST 保留旧响应语义，公共错误码按模块增量登记。

#### Scenario: A new API returns success or error
- **WHEN** `/api/v1` 接口处理普通 JSON 请求
- **THEN** 响应包含 `code=0` 的成功结果或稳定公共/模块错误码、`message`、`data` 和 `requestId`，且 `X-Request-Id` 与 body 中的 `requestId` 一致

#### Scenario: A legacy endpoint is still in compatibility period
- **WHEN** 调用方访问旧 `/admin` 或 `/user` 路由
- **THEN** 系统继续返回旧 `code/msg` 语义，不因新接口迁移而静默改变字段或状态含义

#### Scenario: A special protocol is returned
- **WHEN** 请求是文件上传、异步任务或已经建立的 SSE 连接
- **THEN** 响应遵循对应的 MIME、202/task 状态或 SSE 事件协议，不把普通 JSON envelope 强行套在文件流或 SSE 数据上

### Requirement: Canonical phone data shall be masked only at presentation boundaries

手机号字段 MUST 保存服务端规范化后的原值，用于匹配和唯一约束；响应、日志和审计输出 MUST 使用 `maskPhone` 脱敏，`phone_masked` 不得作为登录字段或唯一索引。

#### Scenario: A phone-bearing record is stored
- **WHEN** 后续认证或客户模块写入手机号
- **THEN** 先完成统一规范化，再使用原值进行服务端匹配和租户内唯一性校验，不把展示掩码写入登录字段

#### Scenario: A phone-bearing record is returned or logged
- **WHEN** 接口、应用日志或审计记录包含手机号
- **THEN** 输出经过 `maskPhone` 的值，原始手机号不进入普通响应或日志
