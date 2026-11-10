# 后端自动化测试说明

环境要求：JDK 21、Maven 3.9.11，以及已启动 Linux 容器引擎的 Docker Desktop。首次运行时需要能够下载 Maven 依赖和指定版本的容器镜像。

在仓库根目录执行以下命令：

```powershell
# 快速单元测试和 HTTP、数据库结构契约检查，不需要 Docker。
mvn -f api/pom.xml test

# 完整验收，包含独立 MySQL、Redis 容器的集成测试。
mvn -f api/pom.xml clean verify
```

完整测试由 Testcontainers 自动创建和清理容器。你只需要启动 Docker Desktop，无需手动创建或启动测试用 MySQL、Redis 容器。宿主机端口动态分配，测试不使用本机安装的 MySQL、已有数据库或固定 Redis 实例，容器复用保持关闭。

测试不调用或复用开发、生产环境的 `docker-compose.yml`，不挂载业务数据目录或命名数据卷，不加入业务网络，也不指定业务容器名称或固定宿主机端口。`withExposedPorts(6379)` 声明的是测试容器内部的 Redis 端口，`getMappedPort(6379)` 获取的是随机分配的宿主机端口。代码已显式关闭容器复用。测试与开发容器共用 Docker 镜像缓存、引擎的 CPU 和内存资源，但中间件数据相互独立。自动化测试应在开发用 Docker Desktop 引擎上运行。

dev Compose 项目 gba-dev 通过 loopback 3307/6380 提供持久 MySQL/Redis，供 IDEA API、前端与 Apifox 联调。这些固定端口不会作为自动化测试的默认连接端口。详见 [本地运行](../deployment/local-development.md)。

加载 Spring 应用上下文的测试显式使用 test。`application-test.yml` 只位于 src/test/resources，不进入主 JAR，也无需个人 application-dev.yml。容器集成测试经 @DynamicPropertySource 注入 testcontainers 连接属性和实际动态端口；缺失属性没有指向 dev 的默认连接。HTTP 契约测试禁用 Flyway，并显式指向不可达的端口1，不使用中间件。Docker 不可用时，完整验收失败并提示启动 Docker Desktop。仅 mvn test 成功不能代表完整验收通过。

Surefire 的单元和契约测试报告位于 `api/target/surefire-reports`，Failsafe 的集成测试报告位于 `api/target/failsafe-reports`。两类 XML 报告均记录实际测试总数、失败数、错误数和跳过数。生成的报告被 Git 忽略，测试源码需要随实现一并提交。

Maven 的 Surefire、Failsafe 会将 `LOG_DIR` 设置为 `api/target/test-logs` 的绝对路径，因此测试日志不会追加到开发或生产日志中。执行 Maven 的 `clean` 阶段时，这些测试日志会被清理。应用运行时通过 `logging.file.path` 或 `LOG_DIR` 配置日志位置：工作目录设为 `api/` 时，开发日志默认写入 `api/.runtime/logs`；生产环境需要将 `LOG_DIR` 指向外部可写日志目录或部署挂载目录。在 IDE 中直接运行测试时，可以通过 JVM 系统属性 `LOG_DIR` 指定输出目录。

MySQL 测试覆盖空库 V1→V6、已有版本升级、重复迁移不改写认证账号/旧行/历史、租户测试数据的幂等性，以及模拟旧数据库结构的显式 baseline 和升级。ChatSchemaIT 另验证 V4→V5，ShipmentSchemaIT 验证 V6 物流表约束。AuthSchemaIT 验证账号表的非空、唯一、状态与同租户创建者约束；AuthPersistenceIT 验证真实 Mapper 索引查询、租户更新/删除边界和本人资源范围。Redis 测试保留旧序列化验证；AuthSmsRedisIT/AuthFlowIT 验证独立 StringRedisTemplate 下的挑战、限流和原子会话状态。

上述测试使用构造的测试数据。生产数据库的备份、恢复演练和结构对比仍需在发布流程中单独执行，不能用这些测试替代。

## 数据分层与 fixture

| 目录 | dev 应用 | test | prod CLI |
| --- | --- | --- | --- |
| db/migration | 是 | 是 | 是 |
| db/seed/common | 是 | 是 | 是 |
| db/seed/nonprod | 是 | 是 | 否 |
| db/devdata | 是 | 否 | 否 |
| src/test/resources/db/fixture | 否 | @Sql 按用例 | 否 |

common 当前没有必需记录；nonprod 是 M_3C_DEMO 和 M_ISOLATION_TEST 两个稳定租户根，生产不加载。
商品/SKU、订单、物流、知识演示数据按模块增量加入 devdata。当前 Agent 已增加 V5 会话表及 R__030_chat_demo.sql，test/prod 不加载这份 devdata。
FoundationApplicationIT 用 @Sql 加载独立的 M_FIXTURE_RUNTIME 租户，按稳定编码定位，并在 @AfterTransaction 验证事务回滚；不依赖业务表总数或空表，不清空共享种子。
AuthPersistenceIT 另用 @Sql 显式加载 db/fixture/auth_accounts.sql，按商户编码关联两个既有租户，提供独立测试员工/消费者及跨租户同号数据；测试事务回滚后按 fixture 主键验证无残留。该 fixture 不在 Flyway seed/devdata 中，测试密码不作为 dev 演示密码。字段/索引与 Snowflake worker 配置见 [认证数据设计](../auth/data-design.md)。
HTTP/异步测试的 fixture 无法由测试事务回滚时，必须按本用例稳定业务标识限定清理。

默认 baseline-version=1 不会使空库跳过 V1；所有普通集成测试从空库按序执行 V1/V2/V3/V4/V5/V6。另保留一个短小旧库接管场景，先验证非空库拒绝，再显式 baseline1 后迁移 V2/V3/V4/V5/V6。
ProductionInfrastructureIT 复用生产初始化脚本验证 DML/迁移权限分离、已有数据目录不重跑 init、Redis 认证和结构＋数据导出恢复；使用临时密码和可销毁实例，不连接实际生产。

QA 可选择在 dev profile 环境人工验收，通过后再进入未来 prod；没有 QA 也可直接进入 prod，无 qa profile。本次没有云发布或 CI/tag 流水线。
资源移动后务必运行 clean verify，清除 target/classes 中残留的 application-local/test 及旧 devdata SQL，再执行 Surefire 和 Failsafe。

test profile 关闭旧业务定时调度（merchant.scheduling.enabled=false），HTTP 契约测试不会在整分钟后台连接中间件；dev/prod 保持原调度行为。

## 认证定向验证（第 4–7 节）

```powershell
mvn -f api/pom.xml '-Dtest=AuthInputTest,AuthSmsConfigurationTest,AuthSmsRedisIT,AuthFlowIT,AuthDevManualIT,AuthContextTest,AuthCredentialTest,JsonLoggingTest,HttpClientTest,FoundationContractTest,RuntimeConfigurationTest' test
```

AuthFlowIT 使用可控时间验证消费者/员工 idle 与 absolute、active 恢复、元数据丢失、刷新加密缓存/重试/重放、并发刷新/退出、租户与本人 SQL 边界、账号停用、数据库/Redis 故障和 503 无 token。独立有效短信挑战在数据库查询屏障后并发插入，证明唯一冲突复用已提交账号；不把同挑战抢消费当成并发建号测试。

AuthDevManualIT 显式启用 dev+test 与专用 namespace、动态临时容器，实际执行 [后端联调脚本](../auth/manual-verification.md) 并导出 Springdoc 到 target。它验证 devdata 重跑不覆盖 hash/status，正常 test/prod locations 排除 devdata；常规 fixture 仍只由 @Sql 提供。故障注入使用测试 spy，不停共享开发中间件。

不清共享表、不 flush Redis、不恢复真实卷。容器销毁清理数据；用例仅操作自己的认证测试前缀。旧 HTTP、上下文清理与日志分流分别由 HttpClientTest、AuthContextTest、JsonLoggingTest 验证。第 9 节全量 test/clean verify 的独立结果见 [认证验收记录](../auth/verification.md)，不以定向结果替代全量验收。

## 种子迁移定向验证（第 8 节）

命令与维护边界见 [开发种子升级](../deployment/dev-seed-upgrade.md)。`DevSeedMigrationIT` 使用独立 MySQL 数据库与真实 Flyway 11.7.2，覆盖排序、无变化重启、显式补种保留人工修改、缺依赖/身份冲突、Spring 启动失败、并发迁移、旧租户 R/两份旧 R 的受控 repair，以及真实 mysqldump 恢复核对和 CLI 执行门槛。旧 SQL 快照只保存在 test resources 的 upgrade-history，运行期不加载。

Maven 不会清除改名资源；本节定向验证前仅移除 target/classes 下两份旧名 R 和旧回调 class 的确切生成文件，不能将残留旧资源带入测试；维护脚本已执行相同检查。第 9 节 clean verify 的结果见 [认证验收记录](../auth/verification.md)。本节自动化不操作开发卷，已有 Docker Compose 开发库升级属于独立、显式维护步骤，不把定向测试通过等同于真实库已升级。

## Agent 与真实认证 HTTP/SSE

```powershell
mvn -f api/pom.xml '-Dtest=ChatAcceptanceIT' test
```

该定向命令需要 Docker。ChatAcceptanceIT 使用 test profile、独立 MySQL 8.0.34/Redis 7.4-alpine、随机端口及显式 @Sql 认证 fixture。消费者短信只替换外部发送器；登录、JWT、Redis 会话验证、Servlet/SSE、Service/Mapper/MySQL 均为真实链路。外部 AgentModel 用可控替身，验证 hello world、幂等 JSON、busy、身份隔离、归档、超时、部分错误、客户端断开与迟到回调、退出后的 401。按 sessionId/runId 检查历史及数据库终态，不依赖整表数量。Redis 仅存认证状态。

ChatSchemaIT/ChatPersistenceIT 验证结构和 Mapper；ChatServiceIT 验证两个独立执行上下文共用数据库的运行权、超时恢复和终态竞争；ChatHttpIT 验证分段 SSE、CORS 与 Springdoc（此类的认证采用替身，不代替 ChatAcceptanceIT）。模型协议和预算由 AgentModelProtocolTest/AgentContextTest 等测试验证。默认 clean verify 会包含 ChatAcceptanceIT，缺失 Docker 明确失败。

ChatHttpIT 的 `clientDisconnectCancelsUpstreamAndLeavesAQueryableTerminal` 用 5 秒等待上游取消，全量并行启动多个容器时偶发超时（2026-11-10 首次全量失败，单类重跑与二次全量均通过）。定位失败时先单独重跑该类：`mvn -f api/pom.xml verify -Dit.test=ChatHttpIT -Dtest=none -DfailIfNoTests=false`，再判断是否为本模块回归。

Apifox 与真实网关联调属于独立验收，不能用这些自动化测试代替。证据、实际报告数量及未完成项见 [Agent 验收记录](../agent/verification.md)；手工步骤见 [Apifox](../agent/apifox-verification.md) 和 [curl/fetch](../agent/manual-verification.md)。

`AgentApifoxWorkspace#serveApifoxOnDisposableInfrastructure` 是供 Apifox 人工验收的显式入口，默认测试发现规则不选择它。它继承真实认证/独立容器设置，仅替换外部短信和模型，应用与 fixture helper 只绑定本机；提供临时登录 token 和按业务标识查询的只读证据。必须人工停止或到时失败，不把“服务启动”记为“Apifox 通过”。启动/停止和可导入用例见上述 Apifox 文档。
