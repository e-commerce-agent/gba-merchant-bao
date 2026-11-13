# application-technical-baseline Specification

## Purpose

定义与 3C 多商户业务匹配的模块化单体技术基线、接口契约、迁移边界和基础设施职责，
使后续实现能够在不重写 Agent 四个亮点的前提下逐步替换旧餐饮模型。

## Requirements

### Requirement: The application shall be organized by business domain

后端 MUST 以认证、商户、商品、库存、订单、物流、会话、知识库、文档任务、Agent 和评测等业务域组织模块；协议转换、业务规则和持久化访问必须有清晰职责边界，Agent 编排不能复制交易域规则。迁移期间旧餐饮代码 MUST 通过明确的兼容边界接入，不能继续扩大跨域直接依赖。

后端工程 MUST 保留在仓库根目录 `api/`；新项目根包 MUST 为 `com.gba.merchantbao`，旧三模块代码 MUST 统一收敛到 `com.gba.merchantbao.legacy`。入口、包声明、扫描配置、Mapper XML、日志和测试引用 MUST 保持一致，不保留两套可编译的重复旧模块源码。

#### Scenario: Package identity is unified
- **WHEN** 构建重命名后的商户宝后端
- **THEN** 新基础组件由 `com.gba.merchantbao` 根包扫描，旧 `/admin` 和 `/user` 由其 legacy 子包提供，构建不依赖 `com.countmaske` 包或旧三模块 POM

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

数据库结构必须（MUST）通过有序且不可变的版本化迁移演进；旧库接管、新商贸表、数据映射和退役步骤必须可追溯。迁移不得删除或重建未经核验的旧表。新开发者、本地新建开发数据库和常规自动化集成测试都必须从空库开始，关闭自动 baseline，并按顺序应用 V1 及后续迁移。现存无 Flyway 历史的旧库必须先备份结构和数据，并将实际结构与 V1 旧库快照逐项核验；只有完全匹配时才允许显式 baseline 到版本 1，随后只运行 V2 及后续迁移。该 baseline 只登记旧结构，不得描述成将旧数据转换为新商贸模型。迁移必须启用校验；失败后必须停止后续发布，采用经验证的结构＋数据备份恢复或评审后的向前修复，不得将删除命名卷、自动 clean 或 repair 当作回滚。

#### Scenario: A fresh database is initialized
- **WHEN** 应用以 `baseline-on-migrate=false` 启动并连接到空数据库
- **THEN** Flyway 不执行手动 baseline，而是按顺序执行 V1、V2、V3 及后续待执行迁移

#### Scenario: A new developer or integration test initializes a database
- **WHEN** 新开发者或自动化集成测试使用全新的空数据库
- **THEN** 初始化从 V1 开始并按顺序执行到最新版本；默认 `baseline-version=1` 不会使空库跳过 V1

#### Scenario: An existing restaurant database is migrated
- **WHEN** 非空旧库已备份，且其表、字段、索引和约束经核验与 V1 一致
- **THEN** 操作者显式登记 baseline 版本 1，随后 Flyway 执行 V2 和 V3，不重跑 V1、不删除旧数据；该操作不代表旧餐饮数据已转换为商贸数据

#### Scenario: A legacy database does not match V1
- **WHEN** baseline 前的结构核验发现缺失、多余或不兼容对象
- **THEN** 停止接管，不创建 baseline 或运行 V2/V3，并记录差异供审查

#### Scenario: An untracked non-empty database is migrated normally
- **WHEN** 数据库已存在业务表但没有 Flyway 历史，且尚未执行显式接管流程
- **THEN** 普通应用启动失败，不得静默 baseline，也不得在已有表上执行 V1

#### Scenario: A migration has already been applied
- **WHEN** 数据库已记录某个迁移版本
- **THEN** 不得原地修改该版本；后续变更使用新版本，并包含校验和失败处理

#### Scenario: A migration fails before application startup
- **WHEN** 迁移失败或校验未通过
- **THEN** 停止后续迁移和新版本应用启动，保留失败记录；经恢复演练后受控恢复迁移前结构和数据，或评审后向前修复，不自动删除数据卷或修改历史来掩盖失败

#### Scenario: An application version is rolled back
- **WHEN** 已迁移数据库后需要回退应用镜像
- **THEN** 先核验旧应用与当前数据库的兼容性；不兼容时按备份恢复方案处理，不能把旧镜像启动等同数据库降级，也不直接覆盖迁移后新增写入

### Requirement: Infrastructure responsibilities shall be explicit

Redis、RabbitMQ、对象存储、向量数据库和关系数据库必须（MUST）有明确职责。中间件依赖必须由第一个使用它的技术模块引入，不能作为尚未实现模块的全局前置条件。dev/prod 必须使用两份独立 Compose，当前只引入所需 MySQL/Redis，数据使用命名卷；dev 端口必须可配置并限 loopback，两环境必须有健康检查和独立项目身份。本地联调状态必须跨服务重启保留；自动化测试必须使用隔离且可销毁的实例。

#### Scenario: Local integration services are started
- **WHEN** 开发者启动文档规定的 `dev` 环境
- **THEN** 根 Compose 在可覆盖的固定端口启动健康的 MySQL/Redis，IDEA 运行的 dev API 连接它们；MySQL 行和 Redis 键在重启或不删卷的容器重建后保留

#### Scenario: Development Redis has no password
- **WHEN** 启动 dev 的 Redis 并连接 IDEA 应用或本地客户端
- **THEN** dev `.env` 不包含 Redis 密码项，Compose 不设置 requirepass，应用不发送密码，健康检查使用无密码 PING；prod 的认证配置不受此规则影响

#### Scenario: Production Redis uses its runtime password
- **WHEN** prod Compose 启动 Redis 和 app
- **THEN** prod 实际 `.env` 的 `REDIS_PASSWORD` 初始值为 `123456`，Redis 读取该值并通过 requirepass 启用认证；健康检查通过 `REDISCLI_AUTH`、app 通过环境变量占位符读取相同密码，不沿用 dev 的无密码连接方式

#### Scenario: Existing middleware volumes are reused
- **WHEN** 修改 `.env` 或 MySQL 初始化脚本后重启已有数据卷
- **THEN** 不把该操作视为已更新数据库账号，需管理员显式变更并同步应用配置；正常更新不删除命名卷，dev/prod 不共用业务卷

#### Scenario: Automated tests run while local services are running
- **WHEN** 开发环境的服务运行在固定端口并使用持久数据卷
- **THEN** 自动化集成测试使用自己的 Testcontainers 实例，不读取或修改开发者的联调数据

#### Scenario: A dependency is unavailable
- **WHEN** Redis、RabbitMQ、向量存储或对象存储暂时不可用
- **THEN** 所属模块遵循已声明的降级或重试约定，并返回可识别的错误；不得把中间件当作唯一业务事实来源

#### Scenario: Agent dependencies integrate with the baseline
- **WHEN** 后续集成既有的四个 Agent 技术亮点
- **THEN** RAG、Function Calling、RabbitMQ 文档任务和 Redis ChatMemory 使用各自领域接口与数据隔离规则，不改变已经确定的业务场景

#### Scenario: A module introduces a new middleware
- **WHEN** 后续技术模块首次需要 Redis 登录会话等中间件
- **THEN** 该模块的 change 声明自己的 Maven 依赖、配置、数据键约定、测试和运维检查；本基础 change 不创建尚未实现模块的数据表或行为

### Requirement: The refactor shall preserve compatibility evidence

重构 MUST 为旧接口和旧前端调用建立保留、适配、弃用或退役清单，并记录每个迁移阶段的回归证据；本 change 不得直接删除尚未确认调用方的旧能力。

#### Scenario: A legacy endpoint is still in use
- **WHEN** 旧 `/admin` 或 `/user` 接口仍有调用方
- **THEN** 系统在兼容期保留或提供适配，并明确新旧语义、认证方式和退役条件

#### Scenario: A legacy endpoint is retired
- **WHEN** 旧接口完成调用方确认、数据对账和回归验证
- **THEN** 系统按记录的退役步骤下线，并保留迁移说明和回退策略

### Requirement: Each technical module shall provide observable test evidence

每个迁移后的技术模块都必须（MUST）根据边界提供适当的单元、集成或契约测试，并输出带请求关联信息的结构化日志。测试源码必须提交。完整自动化验证必须使用 Testcontainers 创建并销毁隔离的 MySQL 和 Redis 实例，使用动态映射主机端口，不得连接本地开发 Compose 服务、固定端口或开发者数据卷。必须关闭容器复用。Docker 不可用时，完整验证必须给出明确指引并失败，不得静默跳过集成测试。测试日志必须与开发者日志隔离。

#### Scenario: Development middleware is already running
- **WHEN** 开发者 Compose MySQL/Redis 正在固定端口和持久卷上运行，且完整测试套件启动
- **THEN** Testcontainers 在随机主机端口启动独立容器，不挂载或连接开发者服务，并在测试后只清理自己创建的容器

#### Scenario: A reviewer runs the complete automated suite
- **WHEN** Docker Desktop 可用且执行文档规定的完整验证命令
- **THEN** 已提交的单元、契约、MySQL/Flyway 和 Redis 集成测试均运行，并报告实际失败、错误和跳过情况

#### Scenario: Docker is unavailable during complete verification
- **WHEN** 完整验证无法访问 Docker
- **THEN** 验证失败并提示启动 Docker Desktop，不得报告完整验证成功

#### Scenario: A module is marked complete
- **WHEN** 技术模块提交验收
- **THEN** 构建证据包含该模块对应的测试、契约结果以及与 change 或提交相关的日志字段检查

#### Scenario: A request fails across a module boundary
- **WHEN** HTTP 请求在控制器、领域模块或外部依赖处失败
- **THEN** 日志包含 `requestId`、模块标识、稳定错误码和相关耗时，响应不得泄露堆栈或密钥

### Requirement: The foundation shall provide deterministic tenant fixtures

数据库初始化必须（MUST）区分结构、全环境基础 reference data、非生产租户根、dev 演示业务数据和测试用例 fixture。`db/migration` MUST 仅包含结构 DDL；`db/seed/common` MUST 仅包含 dev、test、prod 均必需的基础 reference data；`db/seed/nonprod` MUST 包含 dev/test 共用而生产不需要的租户根 `M_3C_DEMO` 与 `M_ISOLATION_TEST`；`db/devdata` MUST 只包含 dev 使用的演示业务数据；测试专用 SQL MUST 位于 `src/test/resources/db/fixture` 并由测试显式使用 `@Sql` 加载，不得进入 Flyway locations。

业务测试 MUST NOT 依赖整张业务表为空或使用整表总数断言来证明单个用例结果；必须按租户和稳定业务标识定位 fixture 记录。事务内的 fixture 应随测试回滚；无法随测试事务回滚的场景必须按稳定业务标识清理，不得清空整表或删除共享种子。

#### Scenario: A fresh test database is initialized
- **WHEN** test profile 在全新的 Testcontainers 数据库执行基础迁移和适用种子
- **THEN** 存在且仅存在业务编码为 `M_3C_DEMO` 与 `M_ISOLATION_TEST` 的非生产租户根，且测试通过业务编码解析 ID；devdata 不加载

#### Scenario: A module adds fixture data
- **WHEN** 商品、订单、物流、知识或会话模块新增演示数据或用例数据
- **THEN** 演示记录放入 `db/devdata`，用例记录放入 `src/test/resources/db/fixture` 并由 `@Sql` 显式加载；业务断言按租户和稳定业务标识定位，不依赖整表总数或空表，无法事务回滚的 fixture 按稳定标识清理，并包含验证租户隔离所需的最小跨租户记录

#### Scenario: Production starts with the foundation
- **WHEN** prod 独立迁移服务执行 Flyway
- **THEN** 加载结构和 `db/seed/common`，不加载 `db/seed/nonprod`、`db/devdata` 或测试 fixture

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

### Requirement: Backend documentation and runtime files shall have explicit locations

根 `README.md` 和整个 `api/docs/prod-profile/` MUST 保持原样；其他后端文档按目录 README 集中导航，architecture、agent、deployment 的长期说明保留，五个基础模块分别冻结，临时验证集中 testing。持续维护 MUST 以 OpenSpec、`docs/interview` 和组织仓库 commit/PR 为准，接口/数据细节分别查生成契约/SQL，不在旧设计中重复同步。本地实际配置不得提交，示例只能包含占位值。dev Compose 读取根 `.env`，IDEA 应用读取被忽略的 `application-dev.yml`，只激活 dev，不要求 IDE 加载 `.env`。test 使用 Testcontainers，不依赖重复的 local profile。测试日志和报告 MUST 留在 `api/target/`，开发日志保留 `.runtime/logs`；个人旧文件备份从 `.runtime/legacy-backup` 迁至忽略的根 `backups/`。

#### Scenario: A developer starts local integration
- **WHEN** 开发者按后端运行手册启动服务
- **THEN** 根 Compose 读取 `.env` 启动持久中间件，IDEA 只激活 dev 并读取 `api/src/main/resources/application-dev.yml`；工作目录为 api，不要求加载 `.env` 或配置额外环境变量，也不依赖 local profile

#### Scenario: Initial connection values are prepared
- **WHEN** 按手册创建本地实际配置
- **THEN** dev MySQL root 与应用密码先使用 `123456`，数据库和用户名与本地 YAML 一致，Redis 不配置密码；prod 实际环境文件的 MySQL/Redis 密码先使用 `123456`，生产 YAML 读取环境变量占位符；未来引入 RabbitMQ 时初始密码亦按此选择设置，不提前新增配置

#### Scenario: Automated tests start the application context
- **WHEN** 集成测试启动应用上下文
- **THEN** 测试显式激活 `test` 并注入 Testcontainers 连接值，不受开发者 `dev` 环境设置影响

#### Scenario: Local configuration is committed
- **WHEN** 检查仓库中提交的本地配置示例
- **THEN** 只跟踪占位值和操作说明；真实凭证及个人本地设置均被忽略，且不会写入日志

#### Scenario: Production preparation is planned
- **WHEN** 准备生产 Compose、环境文件、账号脚本和 Dockerfile
- **THEN** 实际文件分别位于 `api/docs/prod-profile/opt/gba/docker-compose.yml`、该目录的 `.env`、`docker/mysql/init/01-create-users.sh` 和 `app/Dockerfile`；该目录是生产配置唯一来源，Compose、`.env.example`、Dockerfile、`.dockerignore` 和初始化脚本按文件放行提交；实际 `.env`、JAR、发布 SQL 和备份仍忽略，删除重复 examples/prod，测试直接读取该目录；操作手稿可提交，不包含 chmod 600 步骤

#### Scenario: Production artifacts are not released yet
- **WHEN** 本 change 只准备生产配置而尚未上线
- **THEN** 不在准备目录创建 app.jar 或复制 SQL；结构和 common seed 仍保留源码目录，上线时再复制同版本发布产物到 ECS 的 app/、db/migration/ 和 db/seed/common/，不把缺少产物的模板描述为已完成生产构建或部署

#### Scenario: A developer runs Maven verification
- **WHEN** 开发者执行文档规定的 Maven 验证命令
- **THEN** Surefire/Failsafe 的应用测试日志只写入 `api/target/test-logs`，测试报告分别写入 `api/target/surefire-reports` 和 `api/target/failsafe-reports`；测试不得同时创建或修改 `.runtime/logs` 或生产目录中的日志，后端根目录不新增日志或专题文档

#### Scenario: Retired source directories are cleaned
- **WHEN** 旧源码目录已迁移并通过验证
- **THEN** 不保留可重复构建的退役模块源码；必要的个人备份放在被忽略的根 `backups/`，不作为运行依赖

#### Scenario: A developer follows an existing documentation link
- **WHEN** 可修改的当前文档引用了迁移前的说明文件
- **THEN** 按目录 README 的集中导航规则修复定位；根 README 和 prod-profile 字节不变，只有其既有有效调用确实需要时才保留最短兼容入口，不逐文件生成跳转空壳

### Requirement: Runtime profiles shall isolate test configuration and data layers

`application-test.yml` MUST 位于 `src/test/resources`，不得作为应用运行时主资源打包。dev 的应用启动迁移 MUST 加载 migration、common seed、nonprod seed 和 devdata；test MUST 加载 migration、common seed 和 nonprod seed，但 MUST NOT 加载 devdata；prod 应用 MUST 禁用启动迁移，独立迁移服务 MUST 只加载 migration 和 common seed。测试 fixture 由 `@Sql` 显式加载，不属于 Flyway locations。QA 如有则使用 dev profile 环境，通过后进入 prod；无 QA 可直接进入 prod，不要求 qa profile。

#### Scenario: Automated tests use test-only configuration
- **WHEN** Maven 在 test profile 启动 Spring 应用上下文
- **THEN** 配置从 `src/test/resources/application-test.yml` 加载，Testcontainers 提供连接，Flyway 不加载 devdata，且 test fixture 只由测试显式加载

#### Scenario: QA is optional before production
- **WHEN** 发布流程选择是否执行 QA
- **THEN** 有 QA 时在 dev profile 环境验收并在通过后进入 prod；没有 QA 时允许直接进入 prod，不需要另设 qa profile

### Requirement: Production images shall be built before controlled database migration

生产 app 必须（MUST）通过 Dockerfile 将同版本 JAR 构建进镜像，不使用运行期 JAR 挂载。app 必须使用 prod profile 和 DML 账号，禁用启动 Flyway；独立迁移服务必须使用库级迁移账号并保留 Flyway 安全校验。手动流程必须在镜像构建和迁移均成功后才启动新版应用。本 change 只交付准备配置及手册，不执行真实云发布或建设 CI/镜像仓库流水线。

#### Scenario: A production database is initialized
- **WHEN** MySQL 使用全新命名卷首次初始化
- **THEN** 从环境读取密码的 LF 初始化脚本创建迁移账号和应用账号，前者仅获业务库迁移权限、后者仅获 SELECT/INSERT/UPDATE/DELETE；不把 root 或迁移凭据交给 app，已有卷不自动重跑初始化脚本

#### Scenario: A production application image is prepared
- **WHEN** 未来发布已准备同版本 JAR 并执行镜像构建
- **THEN** app 从其 app/ 上下文和 Dockerfile 构建带版本镜像，仅复制 JAR；实际 `.env` 和含 dev 凭据的 YAML 不进入镜像，prod 占位配置可从运行环境取得连接信息

#### Scenario: Manual deployment advances through its gates
- **WHEN** 未来在 ECS 按发布手册更新应用
- **THEN** 先完成必要备份和写入维护，再启动健康中间件，按 build app→独立 migrate→启动新版 app 的顺序执行；任一步失败都停止后续步骤，服务使用容器内地址，prod 不回退到 dev

#### Scenario: Future automation remains deferred
- **WHEN** 本 change 完成生产配置准备
- **THEN** 不包含 RDS、真实 ECS 发布、镜像仓库推拉或 tag 发布流水线；GitHub PR CI 留待 Redis 登录会话模块完成后另行规划，RabbitMQ 等仍随首次使用模块引入

#### Scenario: An existing release is operated without a new deployment
- **WHEN** 已上线同版本服务仅需查看状态、重启、恢复容器或使 app 环境配置生效
- **THEN** 手册分别给出 ps、restart、使用已有镜像 up --no-build、仅重建 app 配置的命令；不要求每次登录 ECS 都构建镜像或执行迁移，restart 不被描述为重新加载环境变量

### Requirement: Database recovery shall use verifiable backups of structure and data

MySQL 备份必须（MUST）同时包含结构和数据，不另做纯数据备份。备份文件必须带日期且不得覆盖同日已有副本；dev 保存到仓库根 backups/ 并忽略 Git；prod 保存到 `/opt/gba/backups/mysql/` 并复制到笔记本。迁移前备份必须能在隔离库恢复，命名卷和同机持久文件不得视为异机备份。Redis 是否备份取决于其状态可重建性。

#### Scenario: A developer exports a database backup
- **WHEN** dev 旧库接管或需保留联调数据前导出 MySQL
- **THEN** 一份如 `backups/gba_dev-data-YYYYmmdd.sql` 的文件同时包含建表和数据，根 backups/ 被 Git 忽略；同日再次导出追加时间避免覆盖，不生成额外纯数据备份

#### Scenario: Production backups are retained off the ECS host
- **WHEN** 生产按日备份或结构变更前额外备份
- **THEN** 先一致性逻辑导出到 `/opt/gba/backups/mysql/`，再下载到笔记本 backups/ 的独立生产目录，核验导出结果和副本，不以复制运行中数据目录代替备份

#### Scenario: A backup is rehearsed for recovery
- **WHEN** 验证迁移前 MySQL 备份能否恢复
- **THEN** 在隔离库恢复并同时核对结构和代表性数据；正式回退需停止写入、检查应用兼容及迁移后新增数据，不自动覆盖业务库或删除命名卷

#### Scenario: Redis state requires preservation
- **WHEN** 后续模块将不可重建状态保存在 Redis
- **THEN** 该模块定义一致性的 RDB/AOF 备份和恢复步骤，生产文件保存到 `/opt/gba/backups/redis/` 后下载到笔记本；仅可重建缓存或可重新登录会话不强制新增备份作业，其他中间件随模块补充原生备份

### Requirement: Runtime logs shall rotate within declared environment boundaries

dev/prod 必须（MUST）输出真实 JSON 的 application.json 与 audit.json，按天和大小滚动压缩；application 为 100MB/7天/2GB，audit 为 100MB/30天/1GB，天数和容量均为归档上限。两份 Compose 的服务 Docker 日志必须为 json-file、20MB×5。prod 日志 bind mount 到 `/opt/gba/logs/app/`，dev 写 `api/.runtime/logs`；测试文件日志只能写 target/test-logs，不新增 error.json。

#### Scenario: Application or audit logs reach their limits
- **WHEN** dev/prod 日志达到滚动大小、日期边界或归档预算
- **THEN** 单文件达到 100MB 或日期切换时滚动压缩，application 最多保留 7 天/2GB 归档，audit 最多保留 30 天/1GB 归档，任一上限先达到可提前清理；活动文件不计入归档总量，不承诺硬磁盘配额

#### Scenario: Production app containers are recreated
- **WHEN** 以保留命名数据卷和日志挂载的方式重建 prod app
- **THEN** 容器 `/app/logs` 映射到 ECS `/opt/gba/logs/app/` 的历史日志仍存在；MySQL/Redis 使用各自命名卷，与应用日志目录分离

#### Scenario: Docker captures development or production service logs
- **WHEN** dev/prod Compose 服务输出 stdout 或 stderr
- **THEN** 每服务 Docker 日志单文件最多 20MB、最多 5 个文件；此规则不代替 Logback 文件轮转或 MySQL binlog 管理，IDEA 运行的 dev 应用不受 Docker 日志驱动控制

#### Scenario: Automated tests write application logs
- **WHEN** Surefire 或 Failsafe 启动应用日志配置
- **THEN** 文件 appender 从初始化起只指向 `api/target/test-logs`，不先写开发目录再切换，不同时写 `.runtime/logs` 或生产路径；控制台输出可以保留，报告仍位于各自报告目录

#### Scenario: An application error is logged
- **WHEN** dev/prod 记录应用异常或审计事件
- **THEN** application.json 包含应用 ERROR，audit.json 独立记录审计，保留关联字段和脱敏；不创建 error.json，不因审计传播或 logger 配置重复写入应用文件

### Requirement: Maintained documentation shall identify authoritative artifacts

维护入口 MUST 将当前行为指向代码、测试与 OpenSpec，将 REST 接口详情指向 Springdoc 导出，将 DDL、种子 DML 与测试数据指向实际 SQL；手写文档 MUST NOT 再复制维护完整接口表和字段表。`docs/interview` MUST 只记录真实实现、取舍和证据。GitHub commit/PR MUST 承载变更和验收证据，不另建平行版本的设计正文。

#### Scenario: An API or database detail is needed
- **WHEN** 开发者或 Agent 查找当前请求字段、表字段、演示数据或用例数据
- **THEN** 入口分别指向 Springdoc、`db/migration/V*.sql`、`db/seed/**/R*.sql`、`db/devdata/R*.sql` 与 `src/test/resources/db/fixture/*.sql`；各 SQL 目录 README 只补充执行范围、顺序、稳定标识和非显然约束

#### Scenario: A generated contract is refreshed
- **WHEN** API 代码发生经批准的变化
- **THEN** `docs/api` 中的接口快照由应用的 Springdoc 生成并关联版本，不能通过手改快照或旧接口设计文档来宣称契约已经更新；SOAP 原有 WSDL/XSD 仍是其契约来源

#### Scenario: Daily documentation is maintained
- **WHEN** 更新后端开发入口
- **THEN** `api/README.md` 保留简短运行与目录入口，各目录 README 链接现有文件并简述用途；不改根 README，不为已完成模块新增缺失的 PRD、接口设计或表设计，也不复制技术长文到 README

### Requirement: Documentation relationships shall be centralized in directory readmes

本次整理的每个实际文档目录及脚本分类目录 MUST 有 README，集中链接文件并说明用途；prod-profile 整体例外。正文 MUST 保留必要语境和技术事实，需要查找其他说明文档时仅链接所属目录 README 的对应条目，不直接互链或改成重复文件名导航。代码、SQL、生成契约的直接证据引用及场景/测试标识 MUST 保留。

#### Scenario: A reader follows the acceptance procedure
- **WHEN** 正文说明隔离服务与14项导入用例已准备且语法检查通过
- **THEN** 保留该事实与结论，并通过本目录 README 的验收条目找到具体说明文件；README 链接该文件并说明服务启动、凭据与用例执行用途，正文不重复维护其文件关系

#### Scenario: A reader checks the interface baseline
- **WHEN** 正文需要说明接口基线或需求矩阵与证据的关系
- **THEN** 保留接口基线语境及 RT/CV 编号对应表，通过所属目录 README 的稳定条目定位具体文档；不因删除直接文档链接而删去测试类、路径、主要断言和验收限制

#### Scenario: A navigation entry crosses directories
- **WHEN** architecture 正文需要引导读者查测试说明或另一个目录的材料
- **THEN** 正文指向 architecture README 的对应条目，由该条目链接目标目录 README 或文件；链接与锚点可解析，architecture 的技术规则保留

### Requirement: Verification history shall be grouped by completed event

临时验证内容 MUST 从模块、architecture 及其他长期文档中迁到 `api/docs/testing/YYYYmmdd-事件/`，日期为事件实际结束日，跨日同一事件不强拆。testing README MUST 每事件一行说明发生原因和记录用途，事件目录 README 链接记录文件。原技术/验收段落 MUST 保留，日常操作说明留所属目录，完成事件不得无限追加后续流水账。

#### Scenario: Architecture contains an old package migration result
- **WHEN** 整理 packages.md 中 2026-11-06 的包迁移验证内容
- **THEN** 原验证段落迁到 testing 的 `20261106-auth-package-migration` 事件目录，命令、数量和结果不改，长期文档不再混放该事件正文；testing README 一行说明原因与用途

#### Scenario: A verification event spans two dates
- **WHEN** 整理跨 2026-11-11 和 2026-11-12 的电商整合验收
- **THEN** 原文件名与正文实际时间保留，放在 `20261112-commerce-integration/`；不会倒置为名称在前的目录，也不以整理日期冒充事件日期

#### Scenario: Another verification is completed later
- **WHEN** 已完成记录之后发生新的验证事件
- **THEN** 新事件另建日期前置目录并在 testing README 新增一行，旧事件不续写；同一事件的材料仍集中，不重抄到业务目录档案

### Requirement: Historical module documents shall be archived

认证、商品、库存、订单、物流 MUST 先整理导航、分离脚本与事件，再分别冻结于 `api/docs/archive/YYYYmmdd-模块/`，日期为实际冻结日，保留现有文件名且不补缺失文档。技术规则、场景编号、测试证据和验收结论 MUST 原文保留，可整段迁移及机械更新实际路径。

#### Scenario: Completed backend designs are archived
- **WHEN** 冻结认证、商品、库存、订单、物流文档
- **THEN** 五个模块分别位于 `api/docs/archive/` 下的同级日期前置目录，各有 README 导航，不混入单个日期容器；临时验收集中 testing，原来缺失的设计不补，模块档案不保留脚本副本

#### Scenario: Existing Agent foundation documents are retained
- **WHEN** 整理当前 Agent 文档
- **THEN** 需求、设计、模型接入与日常操作继续留在 api/docs/agent，通过 README 集中导航；仅临时验证记录迁到 testing，原待验收或不再执行的状态保留，不把整个 Agent 目录归档或宣称后续能力完成

#### Scenario: A later Agent change is implemented
- **WHEN** Agent 工作需要接入已完成业务模块
- **THEN** 默认读取本次 change、相关规范、当前领域服务与对应测试；仅在追溯历史原因时定向读取档案，任何经授权的当前能力变更通过新 change、代码和生成产物表达

#### Scenario: Module documents are archived after organization
- **WHEN** 已完成导航整理、原段落迁移和实际路径更新
- **THEN** 五个模块位于 `api/docs/archive/` 下的同级日期目录，各有 README 导航；不把技术结论改写伪装成导航整理，原 README 中的技术长文迁出后仍可找到

### Requirement: Backend tests shall be discoverable by domain without reducing coverage

测试源码文件 MUST 在 `com/gba/merchantbao/test` 下按业务域、公共基础、跨模块集成和共享辅助职责分目录、分包；仅 ChatRunExecutionTest、ChatSseLifecycleTest 保留原生产包声明以测试包级内部实现，文件仍进入 chat 目录，其他声明与目标目录一致。分包 MUST 保留测试类名、用例与断言、fixture 资源路径及单元/集成命名约定；仅允许下述已确认的 OpenAPI 分组断言归属修正，不得减少覆盖。Maven 默认发现范围与已有手工验收入口的显式选择语义 MUST 保持。完整验证仍使用隔离容器，Docker 不可用时失败。生产代码及 dev/prod profile、配置、运行环境 MUST NOT 因本次分包修改。

#### Scenario: Maintenance verification is recorded in Markdown
- **WHEN** 完成本 change 的来源、保护范围、测试映射与实际发现结果核验
- **THEN** testing 事件的 Markdown 记录来源提交、工作树状态、保护范围、文件映射核对结论、实际发现数量及逐项用例对照结论，并分别记录初始失败与修复结果；原始 XML/日志仍留忽略的 api/target/，临时 JSON 核对文件artifacts/protected-baseline.json、artifacts/test-file-mapping.json、artifacts/test-discovery-baseline.json无需提交或随 change 归档保留，测试和构建不得依赖这些临时文件

#### Scenario: The product contract test checks an authentication schema
- **WHEN** 修正初始基线中 ProductAvailabilityIT 对 StaffLogin 的错误分组断言
- **THEN** product-v1 不包含 StaffLogin，AuthDevManualIT 在 auth-v1 验证 StaffLogin.merchantCode；商品 inStock、公开路径和敏感字段断言保留，不扩大扫描范围或改运行接口，定向测试与最终完整验证通过，初始失败结果保留

#### Scenario: A developer looks for module tests
- **WHEN** 开发者打开某个业务域的测试包
- **THEN** 能在对应的 auth、product、inventory、order、shipment、agent 或 chat 包定位该域测试；跨域验证位于 integration，共享辅助位于 support，测试根包不再平铺 Java 文件

#### Scenario: Maven discovers tests after relocation
- **WHEN** 执行单元验证与完整验证并与迁移前的测试清单比较
- **THEN** 除完整包名变化外发现同一批测试和用例，`*Test` 与 `*IT` 的发现语义不变，无测试因访问权限、继承或选择器变化而消失，失败/错误/跳过数量如实记录

#### Scenario: A test exercises package-private chat internals
- **WHEN** 整理 ChatRunExecutionTest 与 ChatSseLifecycleTest
- **THEN** 文件移入 test/chat，分别保留 com.gba.merchantbao.chat.service、com.gba.merchantbao.chat.controller 包声明和原断言，不扩大生产代码可见性，不改变 dev/prod profile 或运行环境

#### Scenario: A developer explicitly starts the manual acceptance workspace
- **WHEN** 使用文档中的新入口启动原有 Apifox 临时工作区
- **THEN** 入口仍需显式选择，不被普通 Maven 验证自动启动；继承的验收逻辑和本机临时容器隔离仍有效

### Requirement: Maintained scripts shall be organized by execution target

受维护脚本 MUST 分类到 `api/scripts/prod/`、`dev/`、`test/`、`tools/`，只创建有实际文件的目录并各设 README，说明用途、命令和影响目标。脚本迁移 MUST 修复调用路径并保持行为，不放模块档案副本。prod-profile 中原脚本 MUST 原地保留、不复制；旧入口仅在不可改调用方实际需要时保留薄转发。

#### Scenario: A developer chooses an environment script
- **WHEN** 开发者寻找生产发布、dev 维护、隔离联调或通用工具
- **THEN** 从对应目录 README 定位单一脚本实现，不需要打开历史档案找工具；参数、工作目录、执行目标和必要前提可见，真实配置不进入可提交工具

#### Scenario: The protected production manual invokes the old backup path
- **WHEN** 手稿仍调用 api/docs/deployment/backup-dev.ps1
- **THEN** 旧入口只将参数与退出码透传到 scripts/dev 的实现，不保留重复备份逻辑；生产手稿、prod-profile 内脚本原样不动，也不为无调用方的旧脚本路径留空壳

### Requirement: Local runtime storage shall separate state from maintained tooling

`.runtime` MUST 仅保存被 Git 忽略的开发日志、临时文件和本机验收状态，不承担必须的可复用代码或数据库备份。可维护脚本 MUST 受版本控制且可从新检出仓库定位；测试产物 MUST 位于 `target`，备份和一次性历史材料 MUST 位于忽略的根 `backups`。整理 MUST 支持预览、逐项分类与迁后核验，不能递归清空运行目录。

#### Scenario: Existing mixed runtime files are reorganized
- **WHEN** 整理当前日志、SQL 备份、临时源码、编译结果及环境文件
- **THEN** 先输出来源、去向和保留原因，备份迁移后校验哈希；活动日志不移动，正在使用的状态文件不改路径；一次性材料仍可恢复，真实凭据不会被提升为可提交脚本

#### Scenario: A reusable command is needed on a fresh checkout
- **WHEN** 开发者在新检出的仓库执行契约导出或受维护的联调命令
- **THEN** 能从 `api/scripts/` 找到入口，入口不依赖个人绝对路径或 `.runtime` 中遗留的 Java 源码、class、classpath 和旧脚本；运行结果写入约定的输出目录

#### Scenario: A protected manual still names a local environment file
- **WHEN** 不可改动的生产操作手稿引用旧 `.runtime` 环境文件路径
- **THEN** 保留该文件的本地兼容位置并记录用途，不读取或提交凭据正文；后端日志仍使用 `.runtime/logs`，不改生产日志位置
