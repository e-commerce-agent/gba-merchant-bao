# Spec Delta

## MODIFIED Requirements

### Requirement: Schema evolution shall be versioned and reversible

数据库结构必须（MUST）通过有序且不可变的版本化迁移演进；旧库接管、新商贸表、数据映射和退役步骤必须可追溯。迁移不得删除或重建未经核验的旧表。新开发者、本地新建开发数据库和常规自动化集成测试都必须从空库开始，关闭自动 baseline，并按顺序应用 V1 及后续迁移。现存无 Flyway 历史的旧库必须先备份，并将实际结构与 V1 旧库快照逐项核验；只有完全匹配时才允许显式 baseline 到版本 1，随后只运行 V2 及后续迁移。该 baseline 只登记旧结构，不得描述成将旧数据转换为新商贸模型。迁移必须启用校验，并提供失败处理和回滚或降级路径。

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

### Requirement: Infrastructure responsibilities shall be explicit

Redis、RabbitMQ、对象存储、向量数据库和关系数据库必须（MUST）有明确职责。中间件依赖必须由第一个使用它的技术模块引入，不能作为尚未实现模块的全局前置条件。本地开发中间件必须保留状态，以支持前端/API/Apifox 联调；自动化测试必须使用隔离且可销毁的实例。

#### Scenario: Local integration services are started
- **WHEN** 开发者启动文档规定的 `dev` 环境
- **THEN** API 使用配置的本地连接值连接开发者 Compose MySQL 和中间件，数据库状态在服务重启后保留

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

基础配置必须（MUST）在 `dev` 和 `test` 环境提供可复用的样板租户 `M_3C_DEMO` 和隔离对照租户 `M_ISOLATION_TEST`。样板租户代表设计手稿定义的 3C 配件业务；每个业务模块必须在实现时为自己添加固定且可重复写入的样板记录。隔离测试数据必须足以验证该模块的租户边界。生产数据不得加载演示样板。

#### Scenario: A fresh test database is initialized
- **WHEN** 空测试数据库完成结构迁移并启用环境对应的租户种子
- **THEN** 恰好存在上述两个稳定租户业务编码，测试可通过编码查找租户 ID，而不依赖生成 ID

#### Scenario: A module adds fixture data
- **WHEN** 商品、订单、物流、知识或会话模块交付
- **THEN** 模块为自身添加设计手稿中相关的样板数据，使用稳定业务标识和幂等写入，并将数据归属到 `M_3C_DEMO` 或适当的隔离租户

#### Scenario: A module exercises tenant isolation
- **WHEN** 模块执行授权或数据边界测试
- **THEN** 其样板数据包含完成正向和反向断言所需的最小跨租户记录，不得在基础 change 中预建尚未实现模块的表

#### Scenario: Production starts with the foundation
- **WHEN** 后续生产发布中的生产 profile 执行迁移
- **THEN** 只加载结构迁移，不加载开发或测试样板数据

### Requirement: Backend documentation and runtime files shall have explicit locations

后端维护文档必须（MUST）放在 `api/docs/`，根 README 提供导航。本地运行设置必须通过已提交文件之外的方式提供，不能提交密钥。项目只使用 `dev` profile 运行持久化本地联调，并使用 `test` profile 运行 Testcontainers；自动化不得要求或选择重复的 `local` profile。本地 Compose 连接值和凭证必须从被忽略的本地环境配置或开发者环境中加载，已提交示例只能包含占位值。测试输出必须留在 `api/target/`，开发日志放在被忽略的运行目录。

#### Scenario: A developer starts local integration
- **WHEN** 开发者按后端运行手册启动服务
- **THEN** 服务激活 `dev`、启动持久 Compose 服务，并加载开发者本地配置，不依赖第二个 `local` profile

#### Scenario: Automated tests start the application context
- **WHEN** 集成测试启动应用上下文
- **THEN** 测试显式激活 `test` 并注入 Testcontainers 连接值，不受开发者 `dev` 环境设置影响

#### Scenario: Local configuration is committed
- **WHEN** 检查仓库中提交的本地配置示例
- **THEN** 只跟踪占位值和操作说明；真实凭证及个人本地设置均被忽略，且不会写入日志

#### Scenario: Production preparation is planned
- **WHEN** 后续将生产/RDS 发布纳入范围
- **THEN** 通过单独评审的 change 规定密钥注入、备份、迁移演练和发布步骤，不从本地或测试配置中推定生产行为

#### Scenario: A developer runs Maven verification
- **WHEN** 开发者执行文档规定的 Maven 验证命令
- **THEN** 测试日志和报告保留在 `api/target/`，开发运行日志保留在被忽略的运行目录

#### Scenario: Retired source directories are cleaned
- **WHEN** 旧源码目录已迁移并通过验证
- **THEN** 不保留可重复构建的退役模块源码；必要的个人备份放在被忽略的本地运行目录
