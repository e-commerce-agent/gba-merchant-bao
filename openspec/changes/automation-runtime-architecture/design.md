# Design

## Context

当前迁移顺序如下：

- V1 创建从旧库结构快照整理出的 11 张餐饮业务表。
- V2 创建独立的 `merchant` 商户根表。
- V3 为 `merchant` 增加 `currency_code`。

新开发者、本地联调库和自动化集成测试库都从空库开始，按顺序执行 V1、V2、V3。现存旧库只有在实际结构与 V1 完全匹配、且没有 `merchant` 表时，才可以跳过 V1：先备份并完成结构核验，再显式 baseline 到版本 1，随后执行 V2、V3。V2、V3 只增加基础结构，不会把旧餐饮数据转换成 3C 商贸数据。`baseline-version: 1` 只指定显式 baseline 操作记录的版本；当 `baseline-on-migrate: false` 时，空库不会因该设置跳过 V1。

项目目前有 `dev`、`local`、`test`、`prod` 配置。`dev`、`local` 和 `test` 都加载 `db/devdata`；集成测试显式选择 `test`，并通过 Testcontainers 的动态地址覆盖连接配置。当前 `db/devdata` 只有两个租户根记录，尚无 `db/seed` 分层或 test fixture 目录。设计手稿还定义了 3C 商品、SKU、订单、物流、知识和会话演示数据，需随对应模块逐步补充。

本次 Docker 细化以 `api/docs/prod-profile/Docker开发和生产环境.txt` 为输入，并采用本轮已确认的差异：dev Redis 无密码、实际初始密码统一为 `123456`、生产应用使用 Dockerfile 构建镜像、中间件数据命名卷、应用日志 bind mount、本机导出加笔记本异机保存。当前日志实现还是文本 `.log` 和按天轮转；JSON、按大小轮转及归档总量限制是本 change 的实现目标，不能视为已完成。`api/docs/prod-profile/` 当前整体被 Git 忽略。

## Goals / Non-Goals

**Goals:**

- 将本地持久 Docker Compose 服务作为 API、前端和 Apifox 联调的固定环境。
- 使用 Testcontainers 隔离并销毁自动化集成测试依赖。
- 明确空库完整迁移与现存旧库接管是两种不同流程，并分别验证。
- 让结构、所有环境必需的 reference data、非生产租户根、dev 演示业务数据和测试 fixture 各自拥有明确目录与加载边界。
- 支持可选 QA 阶段：QA 使用 dev profile 环境；验收后进入 prod；没有 QA 时可直接进入 prod，不引入单独的 qa profile。
- 测试断言聚焦当前租户和用例稳定业务标识，不将共享环境种子计入被测业务结果。
- 统一 profile，并从已跟踪文件中移除本地凭证。
- 在 Flyway 和 fixture 工作前，先建立独立的 dev/prod Compose 配置及明确的账号、持久化、备份和日志约定。
- 准备可用于未来手动 ECS 发布的 Dockerfile 和迁移账号分离模板；当前不上传、不上线，不复制发布产物。

**Non-Goals:**

- 配置阿里云 RDS、实际部署 ECS，或搭建镜像仓库、GitHub PR CI、tag 发布流水线。PR CI 在 Redis 登录会话完成后再规划。
- 将旧餐饮业务数据转换到新的商品、订单或租户模型。
- 实现认证表、Redis 登录会话或四个 Agent 技术亮点。

## Decisions

### Use `dev` for persistent local integration and `test` for automated tests

使用 Spring 常见的 `dev`/`test` profile，删除重复的 `local`。`application-dev.yml` 连接本地 Compose 服务，并读取开发者提供的连接配置。Compose 使用固定本地端口和命名持久卷。API 在 `dev` 环境运行，前端和 Apifox 连接此 API；这些联调数据不存放在 Testcontainers 中。

仓库根 `docker-compose.yml` 只启动当前需要的 MySQL、Redis；后端仍由 IDEA 运行。根 `.env` 用于 Compose 插值和首次初始化；`api/src/main/resources/application-dev.yml` 保存相同连接配置，IDEA 只需激活 dev、工作目录设为 `api/`，不要求额外设置环境变量或读取 `.env`。两份实际配置被忽略，只跟踪占位示例。MySQL 使用 `gba_dev`、`dev_admin`，root 和应用账号初始密码为 `123456`；dev_admin 拥有该库的 DDL/DML 权限但无全局 root 权限。dev Redis 不设置 `REDIS_PASSWORD`、`requirepass` 或 Spring Redis password，健康检查直接使用 `redis-cli ping`。

dev 默认将 MySQL 映射到 `127.0.0.1:3307`、Redis 映射到 `127.0.0.1:6380`，主机端口可通过 Compose 配置覆盖，并在本地 YAML 中同步。MySQL、Redis 都有健康检查和命名数据卷；Redis 开启 AOF。使用固定且相互独立的项目名（dev 为 `gba-dev`，prod 为 `gba`），避免同机操作时共用数据卷。镜像采用明确版本，MySQL/Redis 的 Compose 与 Testcontainers 版本保持一致，具体版本在实现时结合现有依赖核定，不使用 `latest`。

持久化验证要同时覆盖 MySQL 测试行和 Redis 测试键在重启、容器重建后保留。只更改 `.env` 或初始化脚本不会更新已存在 MySQL 卷中的账号；需管理员修改账号并同步 YAML，不得通过删除卷强制重新初始化。`docker compose down` 保留命名卷，正常开发、迁移和发布步骤不使用 `down -v`。RabbitMQ、Milvus/etcd/MinIO 等待对应模块首次使用再加；届时 RabbitMQ 初始密码按当前选择使用 `123456`，不在本次创建无用途配置。

集成测试显式选择 `test`（例如 `@ActiveProfiles("test")`），由 Testcontainers 启动容器，再通过 `@DynamicPropertySource` 注入实际映射的主机和端口。测试启动保护应拒绝缺失的容器连接属性，避免继承的环境设置将测试意外指向 Compose。完整 Maven 验证是需要 Docker 的自动化测试入口。

### Treat Flyway baseline as an operator action for verified legacy schemas

保持 `baseline-on-migrate=false`、`validate-on-migrate=true`、`clean-disabled=true` 和 `out-of-order=false`。默认 `baseline-version` 设为 `1`，作为显式接管旧库时记录的版本。这个默认值不改变空库初始化：新开发者、本地 Compose 新库及常规 Testcontainers 集成测试都直接执行 V1、V2、V3，不做 baseline。

现存旧库仅按以下步骤接管：

1. 停止应用写入，导出包含结构和数据的一份完整 SQL 备份，并在隔离库恢复验证；不另生成纯数据备份。文件位置和命名见备份决策。
2. 在隔离的演练库中，将目标库与批准的 V1 结构快照逐项比对：表、字段、类型、可空性、索引、约束，以及相关 MySQL 版本和排序规则。
3. 确认目标库没有 `flyway_schema_history` 或冲突的 `merchant` 表，并记录核验结论。
4. 只有核验完全通过后，才显式执行版本 1 的 baseline 操作，再执行 `migrate` 应用 V2、V3；日常应用启动不得自动 baseline。
5. 核对 `flyway_schema_history`、旧表行数、商户新结构、应用启动情况，并确认备份可恢复。

任一结构不匹配都必须停止，不得对部分匹配的库 baseline，也不得用 `repair` 掩盖差异。本 change 使用一个小型独立 Testcontainers 场景验证 Flyway 接管机制和旧行保留；该测试不是完整的线上旧库结构比对工具，也不能证明任意旧库都与 V1 匹配。旧库结构比对由操作手册指导人工执行，不在本次建设大型自动检测工具。

### Use one repeatable tenant-root fixture and incremental business fixtures

按生命周期分开管理数据：

| 数据层 | 目录 | dev | test | prod |
| --- | --- | --- | --- | --- |
| 结构 DDL | `db/migration` | 是 | 是 | 是 |
| 必需 reference data | `db/seed/common` | 是 | 是 | 是 |
| 非生产租户根 | `db/seed/nonprod` | 是 | 是 | 否 |
| 演示业务数据 | `db/devdata` | 是 | 否 | 否 |
| 用例 fixture | `src/test/resources/db/fixture`，由 `@Sql` 加载 | 不适用 | 按用例加载 | 不适用 |

`M_3C_DEMO` 是 3C 配件演示租户，`M_ISOLATION_TEST` 是租户隔离测试根，两者从当前 repeatable 种子移入 `db/seed/nonprod`，供 dev 和 test 共用；生产不创建这两个非生产租户。真正各环境都必须存在的 reference data 才放入 `db/seed/common`。商品、SKU、订单、物流、知识和会话演示记录由对应模块增量加入 `db/devdata`，不进入 test 或 prod。测试场景数据放在 `api/src/test/resources/db/fixture`，测试类或方法通过 `@Sql` 显式加载，不纳入 Flyway migration locations。

加载范围固定为：dev 应用迁移加载 migration、common seed、nonprod seed、devdata；test 加载 migration、common seed、nonprod seed，但排除 devdata；prod 独立 CLI 加载 migration 和 common seed，prod 应用禁用启动迁移。`application-dev.yml` 与 `application-prod.yml` 位于 `api/src/main/resources`；`application-test.yml` 位于 `api/src/test/resources`，避免测试专用 profile 配置进入运行时主资源。QA 如有则使用 dev profile 环境执行验收并在通过后进入 prod；没有 QA 时允许直接进入 prod，本 change 提供手动发布手册，不定义自动发布流水线。

业务测试不以整表总数或空表为前提，也不把 dev/test 共享种子当成测试输入。测试通过稳定业务标识和 `merchant_id`/租户业务编码定位本用例记录；聚合行为只断言明确租户和业务范围内的结果。处于测试事务中的 `@Sql` fixture 由事务回滚；HTTP/异步等无法由测试事务回滚的场景，使用按稳定业务标识限定的清理脚本，不得 TRUNCATE 整表或删除共享租户根。Apifox 使用持久的 dev 数据库及各模块稳定编码。

### Keep production deployment work separate and local secrets untracked

本 change 包含生产准备模板和手动发布手册；RDS、实际云操作和 CI 发布仍延后。dev 的 `.env` 只给 Compose 使用，IDEA 读取本地 YAML；prod 则由 Compose 将 `.env` 的值显式注入 app/migrate，各 YAML 使用环境变量占位符。Spring Boot 不会自动读取 `.env`，两种流程不能混写。

本地生产准备文件布局固定如下，后续 apply 时生成，不在本轮工件编辑中直接创建：

| 文件 | 本地位置 | 未来 ECS 位置 |
| --- | --- | --- |
| prod Compose | `api/docs/prod-profile/opt/gba/docker-compose.yml` | `/opt/gba/docker-compose.yml` |
| prod 实际环境文件 | `api/docs/prod-profile/opt/gba/.env` | `/opt/gba/.env` |
| 账号初始化脚本 | `api/docs/prod-profile/opt/gba/docker/mysql/init/01-create-users.sh` | `/opt/gba/docker/mysql/init/01-create-users.sh` |
| 应用 Dockerfile | `api/docs/prod-profile/opt/gba/app/Dockerfile` | `/opt/gba/app/Dockerfile` |

`api/docs/prod-profile/` 继续忽略。可提交的无凭据 Compose/Dockerfile/初始化脚本及 prod `.env.example` 集中放在 `api/docs/deployment/examples/`；dev `.env.example` 放仓库根，dev/prod 的 YAML 占位示例放文档示例目录。手册说明复制到上表目标位置及填写实际值，使用 `git check-ignore` 验证实际文件被忽略、示例可提交，避免只把示例放到整个被忽略的目录中。实际 prod `.env` 中 MySQL root、迁移账号、应用账号和 Redis 初始密码按当前选择都先用 `123456`；脚本、Dockerfile、已提交示例不硬编码运行凭据，不把 `.env` 复制进镜像。不加入 `chmod 600 /opt/gba/.env` 步骤。

目前不创建发布用 `app.jar`，也不把源码 SQL 复制到上述准备目录。结构脚本继续在 `api/src/main/resources/db/migration/`，common seed 继续在 `api/src/main/resources/db/seed/common/`。未来真实上线时才将同一发布版本的 JAR、DDL、common seed 复制到 ECS `/opt/gba/app/app.jar`、`/opt/gba/db/migration/`、`/opt/gba/db/seed/common/`。当前只有模板时，可检查 Compose 静态配置，但不能声称镜像已构建或生产迁移已运行。

### Build production application images and migrate before startup

prod Compose 有 MySQL、Redis、一次性 `migrate` 和 `app`，与根 dev Compose 分开。中间件默认启动，app/migrate 使用 Compose 的 `deploy` profile（这不是新的 Spring profile）。数据库和 Redis 不开放公网端口；确需本机工具时只映射 loopback，通过 SSH 隧道访问。应用在容器内连接 `mysql:3306`、`redis:6379`，激活 Spring `prod`，不回退到 dev。

prod Redis 从实际 `.env` 的 `REDIS_PASSWORD` 接收密码，初始值按当前选择设为 `123456`，并配置 requirepass；健康检查通过 `REDISCLI_AUTH` 认证，应用通过环境变量占位符读取同一个 `REDIS_PASSWORD`。dev 的无密码约定仅作用于 dev，不能复制到生产配置；“移除 requirepass”仅指 dev Compose。MySQL/Redis 数据仍使用命名卷，应用日志使用 bind mount，两类存储分别管理。

`01-create-users.sh` 使用 LF 换行，从环境读取密码，仅在 MySQL 空数据卷初始化时创建 `flyway_deploy` 与 `app`。前者获得 `gba.*` 的迁移权限，不使用全局 root 或 GRANT OPTION；后者仅有该业务库 SELECT/INSERT/UPDATE/DELETE 权限。root 用于初始化和管理，不交给应用。已有卷需管理员另行创建或授权，不能靠重启补建。

dev 使用同一开发账号，由 Spring Flyway 启动迁移；prod 应用设置 `spring.flyway.enabled=false`，由版本与项目 Flyway 依赖一致的独立 CLI 容器运行。CLI 只接收迁移账号，app 只接收 DML 账号；CLI 挂载同版本 migration 与 common seed，只读加载，不加载 nonprod、devdata 或测试 fixture。保持 validate-on-migrate、clean-disabled 为 true，baseline-on-migrate、out-of-order 为 false，baseline-version 为 1。显式 baseline 是已核验旧库的一次性管理操作，不写进日常发布命令。

Dockerfile 以 Java 21 JRE 为基础，仅复制本地 Maven 已构建的 `app.jar`，构建上下文限定 `./app`。prod Compose 的 app 使用 `build.context: ./app`、该 Dockerfile 和可指定版本的 `gba-app:${APP_VERSION}` 镜像，移除运行期 JAR bind mount，启动命令由镜像提供。构建的 JAR 包含使用变量占位符的 prod 配置，排除包含实际开发凭据的 YAML；这是本 change 要补充的构建契约，不视为现有行为。

未来上传同版本配置及发布产物后，在 ECS `/opt/gba` 的执行顺序统一为：

```sh
docker compose up -d &&
docker compose --profile deploy build app &&
docker compose --profile deploy run --rm migrate &&
docker compose --profile deploy up -d app
```

镜像构建成功才允许迁移，迁移成功才启动新版 app；后两步不得在前一步失败后继续。对已有写流量的发布，先安排停止写入/应用维护并完成备份，再运行迁移，不能以更新镜像代替数据库恢复。手册区分开发电脑执行 scp 和 ECS 执行 Compose 的位置。MySQL 初始化脚本无需先执行 `chmod +x`：官方镜像可 source 可读的 `.sh`，但必须保证 LF 与正确挂载。不上镜像仓库，不做 tag 发布或 GitHub CI；Redis 登录会话完成后另行规划 PR CI。

### Back up structure and data before recovery-sensitive changes

MySQL 使用一致性逻辑导出，一份 SQL 同时包含结构与数据；不使用 `--no-data`、`--no-create-info`，也不再另做纯数据备份。InnoDB 数据导出使用 `--single-transaction` 等适当选项，导出期间禁止并发 DDL；旧库接管或可能影响恢复的迁移前停止业务写入。命名如 `gba_dev-data-YYYYmmdd.sql` / `gba-data-YYYYmmdd.sql`，这里的 `data` 不表示只导出数据。同日再次导出应追加时分秒，避免覆盖已有备份。

dev 在本机导出，保存到仓库根 `backups/`（`A:/work/project/ElectricBusiness/backups/`），整个目录在根 `.gitignore` 中忽略。手册提供适用于 PowerShell 的导出方式，避免终端重定向导致 SQL 编码改变；测试备份恢复使用隔离临时库，不覆盖联调库。生产按日导出，并在结构变更前额外导出到 ECS `/opt/gba/backups/mysql/`，再下载至笔记本的根 `backups/` 下独立生产子目录。核验导出退出码、文件非空、异机副本和恢复结果，不把命名卷或同机备份当异机副本，不直接复制运行中的 MySQL 数据目录。

Redis 开启 AOF、数据保存在命名卷。仅存可重建缓存或可重新登录的会话时，不强制为其新增备份作业；若后续存储不可丢状态，应在所属模块定义一致性 RDB/AOF 备份及恢复步骤，生产保存到 `/opt/gba/backups/redis/` 后下载笔记本。不能把在线 AOF 文件随手复制当成一致性备份。其他中间件在首次引入时约定原生备份方式和 `/opt/gba/backups/<service>/` 路径，不在本次预建。

迁移失败时先停止后续迁移和新应用启动，保留失败记录；不自动 clean、不删命名卷、不用 repair 隐藏差异。需要回退数据库时，停止写入，将迁移前结构＋数据备份恢复到隔离库，核验后再受控切换并使用兼容应用版本；若已有新写入，必须先评估数据损失，不能直接覆盖。另一路径是经评审的后续版本向前修复。旧镜像回退只适用于数据库向后兼容的情况，不等于数据库自动降级；本 change 不承诺通用的 Flyway undo 或自动执行反向 SQL。

### Rotate JSON application logs and isolate test output

dev/prod 都由 Logback 输出真实结构化 JSON 到 `application.json` 和独立的 `audit.json`，保留 requestId、模块、稳定错误码等关联字段和脱敏规则；仅改扩展名不算完成。stdout 便于即时排查，文件用于历史查询；审计 logger 不向应用文件重复传播，相关 logger 不能因独立配置遗漏预期的 stdout。暂不增加 `error.json`，应用 ERROR 保留在 application 日志。

| 输出 | dev | prod | test |
| --- | --- | --- | --- |
| 应用/审计文件 | `api/.runtime/logs/` | 容器 `/app/logs`，bind mount 到 ECS `/opt/gba/logs/app/` | 仅 `api/target/test-logs/` |
| Maven 报告 | 不适用 | 不适用 | `api/target/surefire-reports/`、`api/target/failsafe-reports/` |
| 控制台 | IDEA 控制台 | 容器 stdout/stderr | Maven 测试控制台 |

dev/prod 的 application 文件单文件 100MB，归档最多 7 天、归档总量 2GB；audit 单文件 100MB，归档最多 30 天、归档总量 1GB。按天＋按大小滚动、压缩，时间和容量任一先达到都可清理；归档上限不包含活动文件，也不是整个目录的硬磁盘配额。dev/prod 两份 Compose 均对各服务配置 Docker `json-file`、`max-size: "20m"`、`max-file: "5"`；dev 中此限制只作用于中间件，IDEA 应用不是 Docker 服务。Docker stdout 轮转不代替 Logback 或 MySQL binlog 自身策略。

测试继续由 Surefire/Failsafe 强制设置 LOG_DIR 到 `api/target/test-logs`，只创建指向该目录的文件 appender，不同时启用开发/生产目录的 appender；不能先写 `.runtime/logs` 再切路径。`mvn clean verify` 清除旧 target 后重跑，检查测试新写文件只位于 target，允许开发日志已有历史文件但测试不得修改它们。已归档 spec 的 Maven 日志场景正确，保留历史文件；在本 change 的增量 spec 加强“不双写”的验收描述。实现时同步运行手册、Docker 手稿及 `docs/design/17日志架构文档.txt` 的对应说明，不将 SLS/Loki 等未来平台纳入本次依赖。

## Risks / Trade-offs

- [Risk] 实际旧库结构可能与 V1 不同，即使 V1 来源于已核验快照 → [Mitigation] 显式 baseline 前要求备份、结构比对和隔离演练；任何差异都停止。
- [Risk] 开发者可能将 `dev` 指向旧业务库 → [Mitigation] 使用独立 Compose 数据库名，写明连接目标，不保留个人或类生产默认值；执行 baseline 或迁移前先核验目标。
- [Risk] 持久 Compose 卷会保留旧结构 → [Mitigation] 使用向前迁移并编写仅供开发者使用的重建步骤；自动化测试只操作临时容器，不触碰 Compose 卷。
- [Risk] 移除 `local` 可能影响显式激活该 profile 的脚本 → [Mitigation] 搜索并更新脚本和启动文档，对不支持的 profile 明确报错。
- [Risk] 已跟踪配置曾保存真实凭证 → [Mitigation] 在本次配置修改中替换为环境变量或占位值，并在凭证提供方控制台轮换历史真实凭证。
- [Risk] 修改 `.env` 后已有 MySQL 卷账号不变 → [Mitigation] 说明首次初始化边界，管理员显式变更并同步本地 YAML，不删卷。
- [Risk] 仅有模板时误报生产构建或迁移已验证 → [Mitigation] 当前只做静态配置检查；需要发布 JAR/SQL 的运行验收在隔离演练中使用临时产物，不创建或操作真实生产环境。
- [Risk] 日志落盘两份、备份与镜像共同占用磁盘 → [Mitigation] 分别限制应用归档与 Docker 日志，保留独立备份和恢复说明，不把归档容量等同磁盘配额。

## Migration Plan

1. 完成根 dev Compose、`.env` 和 IDEA 本地 YAML 的配置契约，核验健康检查、命名卷、端口、Redis 无密码及 MySQL/Redis 重启保留数据；测试不复用这些服务。
2. 准备指定目录中的 prod Compose、环境文件、账号脚本和 Dockerfile，以及可提交的占位示例；验证静态配置和手动构建→迁移→启动顺序，不复制发布 JAR/SQL、不上线。
3. 配置 dev/prod JSON 轮转和 Docker 日志限制，保持测试 LOG_DIR 强制隔离；编写结构＋数据导出、异机保存与隔离恢复步骤。
4. 将 `baseline-version` 默认值设为 1，同时保持自动 baseline 关闭；验证空库按 V1 到最新版本迁移、旧库显式 baseline-1 后执行 V2/V3，并验证普通启动拒绝未登记的非空库。
5. 完成 common/nonprod/devdata 与测试 fixture 分层、profile 加载矩阵及相关测试，不改写已应用的 V1/V2/V3。
6. 运行 Maven 分层和完整验证、检查日志无双写，并严格校验本 change。对既有业务库仅提供备份核验和手动接管步骤，不自动修改。

应用和配置变更可通过源代码回退；数据库恢复按上述结构＋数据恢复或经评审的向前修复路径执行。生产旧应用镜像仅在数据库兼容时回退，不把重建容器、删除命名卷或 Flyway validate 当数据库恢复。

## Open Questions

无影响已选范围的待决问题。具体本地端口和 Compose 镜像标签可遵循项目现有约定，并避开开发者已占用的端口。
