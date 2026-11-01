# Proposal

## Why

当前 Flyway 的 baseline 配置、持久联调环境和临时测试环境缺少一致的操作约定；Docker 手稿中的开发、生产、备份与日志流程也需要形成可执行的配置契约。以此细化 Compose 基础配置，再衔接空库 V1→V2→V3、已核验旧库接管和分层样板数据，避免环境配置与迁移规则互相矛盾。

## What Changes

- 明确并验证空数据库与已核验旧数据库的 Flyway 路径；关闭自动 baseline。
- 将 `baseline-version` 默认值统一为 `1`，并说明它只用于显式接管旧库；空库启动不执行 baseline，仍从 V1 开始迁移。
- 使用 `dev` profile 连接本地 Docker Compose MySQL/Redis 持久服务；使用 `test` profile 和独立 Testcontainers 运行自动化测试；移除重复的 `local` profile。
- dev 与 prod 使用两份独立的 `docker-compose.yml`，MySQL/Redis 使用命名持久卷和健康检查；dev 固定端口可覆盖，prod 服务通过容器网络连接。RabbitMQ、Milvus 等仍在首次使用的技术模块中加入。
- 根目录 `.env` 给 dev Compose 使用，被忽略的 `api/src/main/resources/application-dev.yml` 给 IDEA 使用；IDEA 只激活 dev，不加载 `.env`。dev MySQL 默认密码为 `123456`；dev Redis 不配置密码，Compose 不设置 requirepass。prod 实际 `.env` 中 MySQL 和 Redis 初始密码均为 `123456`；Redis 通过 requirepass 启用认证，应用与健康检查读取相同密码。生产连接值通过环境变量注入应用；实际凭据不提交 Git，示例只保留占位值。
- 生产准备文件放在 `api/docs/prod-profile/opt/gba/`：Compose、`.env`、`docker/mysql/init/01-create-users.sh`、`app/Dockerfile`。采用上传 Dockerfile＋JAR 后在 ECS 构建应用镜像的手动流程，不挂载 JAR 启动；本次不复制 JAR 或迁移 SQL 到该目录，也不执行生产发布。
- dev 由应用启动时执行 Flyway；prod 由独立 Flyway CLI 使用迁移账号执行，成功后应用才以 DML 账号启动，prod 应用禁用启动迁移。未来上线时才复制同版本 JAR、结构 SQL 和 common seed。
- MySQL 备份同时包含结构和数据，不另做纯数据备份。dev 导出到仓库根 `backups/`，生产导出到 `/opt/gba/backups/mysql/` 并下载至笔记本；命名带日期，`backups/` 忽略 Git。明确恢复演练、失败后恢复或向前修复的边界，不依靠删除数据卷回滚。
- dev/prod 使用 `application.json` 与 `audit.json`，按天和大小轮转，分别设置 100MB/7天/2GB 与 100MB/30天/1GB 的归档预算；各 Docker 服务日志为 20MB×5。prod 应用日志映射到 `/opt/gba/logs/app/`，dev 写入 `api/.runtime/logs`，测试只写入 `api/target/test-logs`，不新增 `error.json`。
- 明确数据库数据分层：`db/migration` 仅存 DDL；`db/seed/common` 存放 dev/test/prod 都必需的 reference data；`db/seed/nonprod` 存放 dev/test 共用的演示与隔离租户根；`db/devdata` 存放仅 dev 使用的演示业务数据；`api/src/test/resources/db/fixture` 存放由测试通过 `@Sql` 显式加载的用例数据。
- 将 `application-test.yml` 放在 `api/src/test/resources`，并只配置 migration、common seed 和 nonprod seed；dev/prod YAML 保留在 `api/src/main/resources`，dev 应用和 prod 独立 CLI 分别执行对应环境的数据层加载，prod 应用不执行迁移。
- 如需 QA，在 `dev` profile 环境中进行验收，验收通过后进入 `prod` profile；没有 QA 阶段时可直接进入 prod。本 change 提供手动发布说明，不建设自动发布流水线。
- 保留从空库执行 V1 到最新版本的常规集成测试，并以一个小型独立 Testcontainers 场景验证已核验旧库显式 baseline 后执行 V2/V3；不建设大型自动结构比对工具。
- 将 `M_3C_DEMO` 和 `M_ISOLATION_TEST` 作为非生产租户根种子；商品、订单等演示业务样板数据随各自技术模块加入 `db/devdata`，生产不加载这些记录。
- 废弃业务测试对整张表总数或“从 0 增长到 N”的断言，改用租户范围和稳定业务标识验证用例数据；fixture 默认随测试事务回滚，不能事务回滚的场景按稳定标识清理。
- 阿里云 RDS、镜像仓库、GitHub PR CI、tag 发布流水线及真实上线操作不在本次范围内；不为尚未实现的技术模块预建表或加入依赖。PR CI 待 Redis 登录会话完成后另行规划。
- 将实际连接设置和凭证保留在被忽略的本地文件中，提供可提交的占位示例和复制步骤；不加入 `chmod 600` 步骤。

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `application-technical-baseline`：规定 dev/test/prod 的配置和迁移职责、Compose 持久化、备份恢复与日志边界，以及安全的 Flyway 初始化、旧库接管、分层种子和测试 fixture 规则。

## Impact

- 后端配置：`api/src/main/resources/application*.yml`、Flyway 迁移和集成测试配置。
- 开发及生产准备：两套 Compose、命名数据卷、Dockerfile、数据库账号初始化脚本、本地配置与占位示例；生产文件实际放在被忽略的 `api/docs/prod-profile/opt/gba/`。
- 日志与备份：Logback 配置、Docker 日志轮转、`backups/` 忽略规则及备份恢复手册。
- 测试与文档：Flyway/Testcontainers 测试、profile 与 QA 路径、手动发布步骤、种子/fixture 归属及断言规则；后续实现时同步 Docker 手稿和相关运行/日志文档。
- 不包含实际 ECS/RDS 部署、CI 发布流水线、认证表或未来 Agent 技术模块的数据表；已归档 spec 保持历史记录，由本 change 明确新的约定。
