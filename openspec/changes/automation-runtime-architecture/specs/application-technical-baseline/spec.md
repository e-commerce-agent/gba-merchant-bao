# Spec Delta

## MODIFIED Requirements

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

### Requirement: Backend documentation and runtime files shall have explicit locations

后端维护文档必须（MUST）放在 `api/docs/`，根 README 提供导航，包结构与测试文档分别位于 `api/docs/architecture/packages.md`、`api/docs/testing/README.md`。本地实际配置不得提交，示例只能包含占位值。dev Compose 读取根 `.env`，IDEA 应用读取被忽略的 `application-dev.yml`，只激活 dev，不要求 IDE 加载 `.env`。test 使用 Testcontainers，不依赖重复的 local profile。测试日志和报告必须留在 `api/target/`，开发日志及个人旧文件备份分别位于忽略的 `.runtime/logs`、`.runtime/legacy-backup`。

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
- **THEN** 实际文件分别位于 `api/docs/prod-profile/opt/gba/docker-compose.yml`、该目录的 `.env`、`docker/mysql/init/01-create-users.sh` 和 `app/Dockerfile`；该目录保持忽略，安全示例位于可提交的文档目录，操作手册不包含 chmod 600 步骤

#### Scenario: Production artifacts are not released yet
- **WHEN** 本 change 只准备生产配置而尚未上线
- **THEN** 不在准备目录创建 app.jar 或复制 SQL；结构和 common seed 仍保留源码目录，上线时再复制同版本发布产物到 ECS 的 app/、db/migration/ 和 db/seed/common/，不把缺少产物的模板描述为已完成生产构建或部署

#### Scenario: A developer runs Maven verification
- **WHEN** 开发者执行文档规定的 Maven 验证命令
- **THEN** Surefire/Failsafe 的应用测试日志只写入 `api/target/test-logs`，测试报告分别写入 `api/target/surefire-reports` 和 `api/target/failsafe-reports`；测试不得同时创建或修改 `.runtime/logs` 或生产目录中的日志，后端根目录不新增日志或专题文档

#### Scenario: Retired source directories are cleaned
- **WHEN** 旧源码目录已迁移并通过验证
- **THEN** 不保留可重复构建的退役模块源码；必要的个人备份放在被忽略的本地运行目录

## ADDED Requirements

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
