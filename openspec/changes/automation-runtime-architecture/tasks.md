# Tasks

## 1. Local Integration Environment

- [ ] 1.1 在仓库根添加 dev 专用 `docker-compose.yml`，只启动 MySQL/Redis，使用明确版本、固定项目名 `gba-dev`、健康检查、命名持久卷和可覆盖的 loopback 端口（默认 3307/6380）；验证 `docker compose config --quiet` 成功，与对应 Testcontainers 镜像版本一致，不预加 RabbitMQ/Milvus。
- [ ] 1.2 配置 dev 根 `.env` 与本地 `api/src/main/resources/application-dev.yml`：MySQL 数据库 `gba_dev`、用户 `dev_admin`，root/应用初始密码均为 `123456`；dev Redis 不设密码，dev `.env` 不设置 `REDIS_PASSWORD`，dev Compose 移除 requirepass，dev YAML 不配置 Redis password；开启 AOF，健康检查使用无密码 PING。prod Redis 初始密码为 `123456`，认证配置见任务 2.3；验证 IDEA 仅激活 dev 就能连接，不依赖 `.env` 或额外环境变量。
- [ ] 1.3 忽略根 `.env`、实际 dev/prod YAML 和生产 `.env`，提供可提交的占位示例；`api/docs/prod-profile/` 保持忽略，安全示例放未被忽略的 `api/docs/deployment/examples/`；验证 `git check-ignore` 的预期结果，并验证实际凭据未进入已跟踪文件。
- [ ] 1.4 移除重复的 `application-local.yml` 及其启动引用；验证配置和文档使用 dev/test/prod，测试显式激活 test。记录历史真实凭据应在服务提供方轮换。
- [ ] 1.5 在 dev 卷写入 MySQL 临时行和 Redis 临时键，验证重启及不删卷的容器重建后均保留，再定向清理；运行手册解释已有卷修改 `.env` 不会更新账号、管理员变更后需同步 YAML，正常流程不用 `down -v`。
- [ ] 1.6 更新本地运行手册和前端/Apifox 连接说明，明确 dev API 由 IDEA 运行、工作目录为 api、Compose 只启动中间件；按占位示例完成一次干净配置演练，验证没有强制 IDEA 注入环境变量的旧说明。

## 2. Production Preparation

- [ ] 2.1 在 `api/docs/prod-profile/opt/gba/` 准备独立 prod `docker-compose.yml` 和 `.env`，在 `docker/mysql/init/` 准备 `01-create-users.sh`，在 `app/` 准备 Dockerfile，并给出对应占位示例；验证目录、两套 Compose 隔离和 prod 静态配置，不在准备目录创建 app.jar 或复制 SQL。
- [ ] 2.2 生产 MySQL 空卷初始化读取 root、迁移、应用密码，创建业务库 `gba`、`flyway_deploy` 和 `app`，分别授予库级迁移权限和 DML 权限；实际 `.env` 密码先统一 `123456`，脚本不硬编码密码且使用 LF。通过隔离演练验证权限和已有卷不自动重跑脚本的行为，不操作真实生产库。
- [ ] 2.3 prod 应用显式激活 prod、禁用启动 Flyway，使用容器内 MySQL/Redis 地址和环境变量占位符；prod 实际 `.env` 设置 `REDIS_PASSWORD=123456` 作为初始值，Redis 通过 requirepass 启用认证，健康检查通过 `REDISCLI_AUTH`、应用通过环境变量占位符读取相同密码，并验证认证健康检查。独立 CLI 只获得迁移账号，加载 migration/common seed，与项目 Flyway 版本及安全开关一致；通过配置契约与隔离 MySQL 验证账号分离及迁移成功后才启动应用。
- [ ] 2.4 Dockerfile 仅复制 Java 21 应用 JAR，prod app 使用 `./app` 构建上下文和可指定版本镜像，删除运行期 JAR 挂载；验证构建定义和打包契约（prod 占位配置进入 JAR，实际 dev 凭据配置不进入 JAR），不因缺少未来发布 JAR 而预放空文件或复制生产产物。
- [ ] 2.5 编写手动 ECS 发布手册：未来从开发电脑上传同版本 JAR、源码目录中的 migration/common seed 和配置到 `/opt/gba`，ECS 先启动中间件，再按 build app→migrate→up app 的成功链执行；同步 Docker 手稿中旧 JAR 挂载版和第 7 节执行顺序，核对命令与 Compose deploy profile，不加入 chmod 600、RDS、镜像仓库或 CI/tag 发布任务。

## 3. Logs and Backup Recovery

- [ ] 3.1 dev/prod 的 Logback 输出真实 JSON：application.json 使用 100MB/7天/2GB，audit.json 使用 100MB/30天/1GB，按天和大小滚动压缩；保留请求关联与脱敏，暂不增加 error.json；用聚焦的日志配置/输出验证检查 JSON、字段和轮转规则，不新增庞大测试框架。
- [ ] 3.2 dev 日志定位到 `api/.runtime/logs`，prod `/app/logs` bind mount 到 `/opt/gba/logs/app/`，两套 Compose 的各服务均配置 Docker json-file 20MB×5；验证配置及容器重建后日志保留，说明归档容量不含活动文件、Docker 日志限制不约束 IDEA 应用或 MySQL binlog。
- [ ] 3.3 保持 Surefire/Failsafe 强制 LOG_DIR 为 `api/target/test-logs`，报告位于 `api/target/surefire-reports` 和 `api/target/failsafe-reports`；运行 Maven 测试验证不向 `.runtime/logs` 或生产路径双写，不修改已有开发日志，同步运行/日志手册并保留正确的归档 spec。
- [ ] 3.4 编写 PowerShell 本地导出、ECS 导出和笔记本下载说明：一份 SQL 包含结构＋数据，dev 存根 `backups/`，prod 存 `/opt/gba/backups/mysql/` 后下载笔记本，带日期且同日不覆盖；根 `.gitignore` 忽略 backups/。通过隔离 MySQL 导出/恢复演练验证结构及代表性行都恢复，核对退出码和文件，不操作真实生产。
- [ ] 3.5 写明生产按日和结构变更前额外备份、Redis 可重建数据按需处理及需备份时的 RDB/AOF 方式和 `/opt/gba/backups/redis/` 路径；同步 Docker/迁移手册，验证恢复、应用兼容回退及向前修复步骤完整，禁止自动 clean、删卷或用 repair 掩盖迁移失败，不预建其他中间件备份作业。

## 4. Flyway Baseline Workflow

- [ ] 4.1 将应用和独立 CLI 的默认 `baseline-version` 设为 `1`，并保持 `baseline-on-migrate=false`、启用校验、禁用 clean 和 out-of-order；验证空 MySQL Testcontainers 无需手动 baseline，按顺序执行 V1、V2、V3，不改已应用迁移。
- [ ] 4.2 保留一个小型独立旧库接管场景：V1 SQL 构建旧结构并插入代表性数据，确认普通 migrate 拒绝未登记非空库；显式 baseline-1 后只执行 V2/V3，旧行仍在。不建设大型自动结构差异检测工具。
- [ ] 4.3 编写旧库接管手册，明确迁移前结构＋数据备份、逐项核验和停止条件、显式 baseline、migrate 与历史/行数核对；在隔离演练库验证命令及恢复步骤，确认日常启动/发布不自动 baseline。
- [ ] 4.4 验证新开发者的空数据库、本地新建 Compose 数据库和常规自动化集成测试都从 V1 开始；baseline-1 只用于已核验旧库接管，不跳过空库 V1，也不把旧数据转换成新商贸模型。

## 5. Fixtures and Isolation

- [ ] 5.1 将所有环境必需的 reference data 约定在 `db/seed/common`，将 `M_3C_DEMO` 与 `M_ISOLATION_TEST` 从 devdata 移入 `db/seed/nonprod`；为 dev/test/prod 对应迁移执行方配置 locations，验证两租户仅在 dev/test 初始化且重复应用幂等。
- [ ] 5.2 将 `application-test.yml` 移至 `src/test/resources`，配置 test 只加载 migration、common seed 和 nonprod seed；dev 加载四层运行数据，prod CLI 排除 nonprod seed 和 devdata；通过配置契约测试验证矩阵及 prod 应用不自动迁移。
- [ ] 5.3 将 `db/devdata` 限定为 dev 专用演示业务数据，记录设计手稿中商品/SKU、订单、物流、知识和会话样板的模块归属；验证 prod/test 不加载演示数据，本基础 change 不预建未实现模块表。
- [ ] 5.4 建立 `src/test/resources/db/fixture` 约定，按测试方法或类使用 `@Sql` 加载；业务断言改按租户和稳定业务标识定位，不依赖全表总数或空表，并验证事务回滚或按标识清理。
- [ ] 5.5 更新测试与运行文档，说明 test-only YAML、fixture、QA 可选使用 dev 及后续 prod 路径，以及 clean verify 清理旧 target 后重跑完整测试；验证文档与 Surefire/Failsafe 阶段一致，不引入 qa profile 或 CI 发布。
- [ ] 5.6 保留 Testcontainers 隔离检查，证明测试不连接固定 Compose 端口、不挂载业务命名卷；在本地 Compose 服务运行时执行完整集成测试验证，测试只清理自身资源。

## 6. Full Verification

- [ ] 6.1 运行 `mvn -f api/pom.xml test`，确认单元/配置测试通过且不依赖本地 MySQL/Redis，同时确认报告及日志路径。
- [ ] 6.2 Docker 可用时运行 `mvn -f api/pom.xml clean verify`，确认旧 target 已清理、Surefire/Failsafe 实际运行、Testcontainers 启动 MySQL/Redis 且日志无双写；生产模板验证与真实上线状态分别记录。
- [ ] 6.3 严格校验 change，核对四份工件与实现/操作手册的路径、凭据来源、备份、日志、迁移和数据分层一致；确认未加入未来模块、中间件、RDS、CI 或 tag 发布。
